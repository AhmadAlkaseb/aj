"""Generate local Kubernetes resources from the reviewed Compose configuration.

Secrets and TLS keys are generated separately into ignored test-results/k8s.
"""
import copy
import json
import subprocess
from pathlib import Path

ROOT=Path(__file__).resolve().parent.parent
NS='aj'


def main():
    config=json.loads(subprocess.check_output(['docker','compose','config','--format','json'],cwd=ROOT))
    items=[{'apiVersion':'v1','kind':'Namespace','metadata':{'name':NS}}]
    def resource(kind,name,spec,api='v1'):
        obj={'apiVersion':api,'kind':kind,'metadata':{'name':name,'namespace':NS},'spec':spec};items.append(obj);return obj
    def secret(key):return {'valueFrom':{'secretKeyRef':{'name':'aj-secrets','key':key}}}
    realm=json.loads((ROOT/'identity/aj-realm.json').read_text(encoding='utf-8'))
    client=realm['clients'][0];client['redirectUris']+=['https://localhost:8443/*'];client['webOrigins']+=['https://localhost:8443']
    client['attributes']['post.logout.redirect.uris']+='##https://localhost:8443/*'
    items.append({'apiVersion':'v1','kind':'ConfigMap','metadata':{'name':'aj-realm','namespace':NS},'data':{'aj-realm.json':json.dumps(realm)}})
    apps=['users','products','orders','payments','product-view','product-synchronizer','apigateway','frontend']
    infra=['users-db','products-db','orders-db','payments-db','keycloak-db','product-view-mongo','rabbitmq','keycloak']
    for name in infra+apps:
        source=config['services'][name];labels={'app':name,'tier':'application' if name in apps else 'infrastructure'}
        env=[]
        for key,value in source.get('environment',{}).items():
            if key=='DB_BASELINE_EXISTING':value='false' # Kubernetes starts its own empty volumes.
            if key in ['DB_PASSWORD','POSTGRES_PASSWORD','KC_DB_PASSWORD']:entry=secret('database-password')
            elif key=='RABBITMQ_DEFAULT_PASS':entry=secret('rabbit-password')
            elif key=='KC_BOOTSTRAP_ADMIN_PASSWORD':entry=secret('identity-admin-password')
            elif key=='RABBITMQ_URL':entry=secret('rabbit-url')
            elif key=='PROJECTION_API_TOKEN':entry=secret('projection-token')
            elif key=='CONFIRMATION_TOKEN':entry=secret('confirmation-token')
            else:
                if key=='OIDC_ISSUER':value='https://localhost:8443/identity/realms/aj'
                if key=='OIDC_JWKS_URL':value='http://keycloak:8180/identity/realms/aj/protocol/openid-connect/certs'
                if key=='KEYCLOAK_URL':value='http://keycloak:8180/identity'
                if key=='RABBITMQ_NODENAME':value='rabbit@rabbitmq'
                if key=='KC_HOSTNAME':value='https://localhost:8443/identity'
                entry={'value':str(value)}
            env.append({'name':key,**entry})
        if name=='frontend':env.append({'name':'OIDC_PUBLIC_URL','value':'https://localhost:8443/identity'})
        if name=='keycloak':
            env.append({'name':'KC_HTTP_RELATIVE_PATH','value':'/identity'})
            env.append({'name':'KC_PROXY_HEADERS','value':'xforwarded'})
        port=8180 if name=='keycloak' else 5432 if name.endswith('-db') else 27017 if name=='product-view-mongo' else 5672 if name=='rabbitmq' else 7000
        container={'name':name,'image':source['image'],'imagePullPolicy':'Never' if name in apps else 'IfNotPresent',
            'env':env,'ports':[{'name':'http' if port in [7000,8180] else 'data','containerPort':port}],
            'resources':{'requests':{'cpu':'50m','memory':'128Mi'},'limits':{'cpu':'1000m','memory':'512Mi'}}}
        volumes=[]
        if name in apps:
            container['securityContext']={'runAsUser':10001,'runAsGroup':10001,'runAsNonRoot':True,'allowPrivilegeEscalation':False,'capabilities':{'drop':['ALL']}}
            container['readinessProbe']={'httpGet':{'path':'/health','port':7000},'periodSeconds':5,'timeoutSeconds':5}
            container['livenessProbe']={'httpGet':{'path':'/health/live','port':7000},'periodSeconds':10,'timeoutSeconds':3}
            container['startupProbe']={'httpGet':{'path':'/health/live','port':7000},'periodSeconds':5,'failureThreshold':36}
        if name=='keycloak':
            container['args']=source['command'];container['resources']['limits']['memory']='768Mi'
            volumes.append({'name':'realm','configMap':{'name':'aj-realm'}})
            container['volumeMounts']=[{'name':'realm','mountPath':'/opt/keycloak/data/import','readOnly':True}]
            container['readinessProbe']={'httpGet':{'path':'/identity/realms/aj/.well-known/openid-configuration','port':8180},'periodSeconds':5,'timeoutSeconds':3}
        if name.endswith('-db'):
            container['readinessProbe']={'exec':{'command':['pg_isready','-U',source['environment']['POSTGRES_USER'],'-d',source['environment']['POSTGRES_DB']]},'periodSeconds':5}
        if name=='rabbitmq':
            container['command']=source['command'];container['ports']+=[{'name':'management','containerPort':15672},{'name':'metrics','containerPort':15692}]
            container['readinessProbe']={'exec':{'command':['rabbitmq-diagnostics','-q','ping']},'periodSeconds':10,'timeoutSeconds':5}
        if name=='product-view-mongo':container['resources']['limits']['memory']='768Mi'
        for value in source.get('volumes',[]):
            if value['type']!='volume':continue
            claim=name+'-data'
            resource('PersistentVolumeClaim',claim,{'accessModes':['ReadWriteOnce'],'resources':{'requests':{'storage':'1Gi'}}})
            volumes.append({'name':'data','persistentVolumeClaim':{'claimName':claim}})
            container.setdefault('volumeMounts',[]).append({'name':'data','mountPath':value['target']})
        pod={'containers':[container]}
        if name=='rabbitmq':pod['hostname']='rabbitmq'
        if volumes:pod['volumes']=volumes
        if name in ['users','products','orders','payments']:
            init=copy.deepcopy(container);init['name']='migrate';init['command']=['java','-cp','/app/app.jar','com.aj.common.Migrate',name]
            for key in ['ports','readinessProbe','livenessProbe','startupProbe']:init.pop(key,None)
            pod['initContainers']=[init]
        workers=['users','products','orders','payments','product-synchronizer']
        resource('Deployment',name,{'replicas':1,'selector':{'matchLabels':{'app':name}},'strategy':{'type':'Recreate'} if name in infra+workers else {'type':'RollingUpdate'},'template':{'metadata':{'labels':labels},'spec':pod}},'apps/v1')
        ports=[{'name':'http' if port in [7000,8180] else 'data','port':port,'targetPort':port}]
        if name=='rabbitmq':ports += [{'name':'management','port':15672,'targetPort':15672},{'name':'metrics','port':15692,'targetPort':15692}]
        resource('Service',name,{'selector':{'app':name},'ports':ports})
    # Enforced with Calico; only each owning application can reach its database.
    resource('NetworkPolicy','default-deny-ingress',{'podSelector':{},'policyTypes':['Ingress']},'networking.k8s.io/v1')
    owners={**{x+'-db':x for x in ['users','products','orders','payments','keycloak']},'product-view-mongo':'product-view'}
    for database,owner in owners.items():
        resource('NetworkPolicy','owner-'+database,{'podSelector':{'matchLabels':{'app':database}},'policyTypes':['Ingress'],
            'ingress':[{'from':[{'podSelector':{'matchLabels':{'app':owner}}}]}]},'networking.k8s.io/v1')
    for app in apps+['keycloak','rabbitmq']:
        senders=['apigateway']
        if app=='frontend':senders=['traefik']
        elif app=='keycloak':senders=apps+['traefik']
        elif app=='rabbitmq':senders=apps+['confirmation-job']
        elif app=='orders':senders+=['confirmation-job']
        elif app=='product-view':senders+=['product-synchronizer']
        elif app=='apigateway':senders+=['frontend']
        resource('NetworkPolicy','callers-'+app,{'podSelector':{'matchLabels':{'app':app}},'policyTypes':['Ingress'],
            'ingress':[{'from':[{'podSelector':{'matchLabels':{'app':sender}}} for sender in senders]}]},'networking.k8s.io/v1')
    # The KEDA operator is outside the app namespace; grant only management access to the broker.
    resource('NetworkPolicy','keda-broker',{'podSelector':{'matchLabels':{'app':'rabbitmq'}},'policyTypes':['Ingress'],
        'ingress':[{'from':[{'namespaceSelector':{'matchLabels':{'kubernetes.io/metadata.name':'keda'}}}], 'ports':[{'port':5672},{'port':15672}]}]},'networking.k8s.io/v1')
    resource('HorizontalPodAutoscaler','frontend',{'scaleTargetRef':{'apiVersion':'apps/v1','kind':'Deployment','name':'frontend'},'minReplicas':1,'maxReplicas':2,
        'metrics':[{'type':'Resource','resource':{'name':'cpu','target':{'type':'Utilization','averageUtilization':60}}}],
        'behavior':{'scaleUp':{'stabilizationWindowSeconds':0},'scaleDown':{'stabilizationWindowSeconds':60}}},'autoscaling/v2')
    resource('TriggerAuthentication','confirmation-broker',{'secretTargetRef':[{'parameter':'host','name':'aj-secrets','key':'rabbit-url'}]},'keda.sh/v1alpha1')
    resource('ScaledJob','confirmation',{'pollingInterval':5,'minReplicaCount':0,'maxReplicaCount':2,
        'successfulJobsHistoryLimit':3,'failedJobsHistoryLimit':3,'jobTargetRef':{'backoffLimit':2,'activeDeadlineSeconds':60,
            'template':{'metadata':{'labels':{'app':'confirmation-job'}},'spec':{'restartPolicy':'Never','containers':[{'name':'confirmation',
                'image':'aj-confirmation-job:local','imagePullPolicy':'Never','env':[{'name':'RABBITMQ_URL',**secret('rabbit-url')},{'name':'ORDERS_URL','value':'http://orders:7000'},{'name':'CONFIRMATION_TOKEN',**secret('confirmation-token')}],
                'resources':{'requests':{'cpu':'50m','memory':'128Mi'},'limits':{'cpu':'500m','memory':'256Mi'}}}]}}},
        'triggers':[{'type':'rabbitmq','metadata':{'queueName':'aj.confirmations','mode':'QueueLength','value':'1','protocol':'amqp'},'authenticationRef':{'name':'confirmation-broker'}}]},'keda.sh/v1alpha1')
    # Minimal ingress controller, scoped to the aj namespace, with local TLS.
    items.append({'apiVersion':'v1','kind':'ServiceAccount','metadata':{'name':'traefik','namespace':NS}})
    items.append({'apiVersion':'rbac.authorization.k8s.io/v1','kind':'Role','metadata':{'name':'traefik','namespace':NS},'rules':[
        {'apiGroups':[''],'resources':['services','endpoints','secrets'],'verbs':['get','list','watch']},
        {'apiGroups':['discovery.k8s.io'],'resources':['endpointslices'],'verbs':['get','list','watch']},
        {'apiGroups':['networking.k8s.io'],'resources':['ingresses'],'verbs':['get','list','watch']},
        {'apiGroups':['networking.k8s.io'],'resources':['ingresses/status'],'verbs':['update']} ]})
    items.append({'apiVersion':'rbac.authorization.k8s.io/v1','kind':'RoleBinding','metadata':{'name':'traefik','namespace':NS},'roleRef':{'apiGroup':'rbac.authorization.k8s.io','kind':'Role','name':'traefik'},'subjects':[{'kind':'ServiceAccount','name':'traefik','namespace':NS}]})
    resource('Deployment','traefik',{'replicas':1,'selector':{'matchLabels':{'app':'traefik'}},'template':{'metadata':{'labels':{'app':'traefik'}},'spec':{'serviceAccountName':'traefik','containers':[{'name':'traefik','image':'traefik:v3.3.3',
        'args':['--providers.kubernetesingress=true','--providers.kubernetesingress.namespaces=aj','--providers.kubernetesingress.disableclusterscoperesources=true','--entrypoints.websecure.address=:8443','--entrypoints.websecure.http.tls=true'],
        'ports':[{'containerPort':8443}],'resources':{'requests':{'cpu':'25m','memory':'64Mi'},'limits':{'cpu':'500m','memory':'128Mi'}}}]}}},'apps/v1')
    resource('NetworkPolicy','ingress-controller',{'podSelector':{'matchLabels':{'app':'traefik'}},'policyTypes':['Ingress'],'ingress':[{}]},'networking.k8s.io/v1')
    resource('Service','traefik',{'type':'NodePort','selector':{'app':'traefik'},'ports':[{'port':8443,'targetPort':8443,'nodePort':30443}]})
    resource('Ingress','aj',{'tls':[{'hosts':['localhost'],'secretName':'aj-tls'}],'rules':[{'host':'localhost','http':{'paths':[
        {'path':'/identity','pathType':'Prefix','backend':{'service':{'name':'keycloak','port':{'number':8180}}}},
        {'path':'/','pathType':'Prefix','backend':{'service':{'name':'frontend','port':{'number':7000}}}}]}}]},'networking.k8s.io/v1')
    destination=ROOT/'k8s/base.json';destination.write_text(json.dumps({'apiVersion':'v1','kind':'List','items':items},indent=2)+'\n',encoding='utf-8')
    print('Generated Kubernetes resources without embedding deployment secrets.')


if __name__=='__main__':main()
