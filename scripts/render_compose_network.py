"""Render the Compose network view using the supplied teacher example's container icon."""
from pathlib import Path
import base64
import json
import urllib.parse
import xml.etree.ElementTree as E
import zlib

ROOT=Path(__file__).resolve().parent.parent


def main():
    cfg=json.loads((ROOT/'docker-compose.yml').read_text())
    teacher=E.parse(ROOT/'documentation/report-figures/microservices-scaling (1).drawio').getroot().find('diagram')
    model=teacher.find('mxGraphModel')
    if model is None:
        model=E.fromstring(urllib.parse.unquote(zlib.decompress(base64.b64decode(teacher.text),-15).decode()))
    icon=next(c.get('style') for c in model.findall('./root/mxCell') if c.get('id')=='eP0o5e_dgutJD-ohwvqB-94')
    m=E.Element('mxGraphModel',{'page':'0','background':'#ffffff'})
    r=E.SubElement(m,'root')
    E.SubElement(r,'mxCell',{'id':'0'});E.SubElement(r,'mxCell',{'id':'1','parent':'0'})

    def box(id,label,x,y,w,h,style,parent='1'):
        c=E.SubElement(r,'mxCell',{'id':id,'value':label,'style':style,'vertex':'1','parent':parent})
        E.SubElement(c,'mxGeometry',{'x':str(x),'y':str(y),'width':str(w),'height':str(h),'as':'geometry'})
        return c

    text='text;html=1;whiteSpace=wrap;align=center;verticalAlign=middle;fontSize=22;fontColor=#1f2937;'

    def label(id,t,x,y,w,h=32,size=22):
        return box(id,t,x,y,w,h,text+f'fontSize={size};')

    def boundary(id,t,x,y,w,h,color):
        return box(id,t,x,y,w,h,f'rounded=1;arcSize=6;whiteSpace=wrap;html=1;container=1;pointerEvents=0;fillColor=none;strokeColor={color};dashed=1;dashPattern=8 5;strokeWidth=3;fontSize=23;fontStyle=1;fontColor={color};align=left;verticalAlign=top;spacingLeft=14;spacingTop=10;')

    label('title','Docker-Compose system architecture — AJ Motors',0,0,1770,50,32)
    boundary('host','Localhost / Docker host — 127.0.0.1',20,65,1730,100,'#247338')
    label('host-note','Published ports use host:container mapping; no fixed container IPs are required.',450,115,1200,30,20)
    boundary('backend','',300,205,980,1020,'#cf3636')
    heading=label('backend-label','aj-backend',650,250,250,35,24)
    heading.set('style',heading.get('style')+'fontColor=#cf3636;fontStyle=1;')
    boundary('edge','aj-edge',30,190,550,180,'#577fb5')
    owners=[('users','users-db','users-data'),('products','products-db','products-data'),('orders','orders-db','orders-data'),('payments','payments-db','payments-data'),('product-view','product-view-mongo','product-view-data'),('keycloak','keycloak-db','identity-data')]
    for i,(api,db,net) in enumerate(owners):
        boundary('net-'+net,'',1000,385+i*132,750,122,'#da8300')
        heading=label('net-label-'+net,'aj_'+net,1235,390+i*132,220,30,18)
        heading.set('style',heading.get('style')+'fontColor=#da8300;fontStyle=1;')
    seen=set()

    def node(service,x,y):
        v=cfg['services'][service];name=v.get('container_name',service);seen.add(service)
        group=box('container-'+service,'',x,y,240,110,'group;pointerEvents=0;')
        group.set('networks',','.join(v.get('networks',[])))
        box('icon-'+service,'',85,3,70,44,icon,'container-'+service)
        box('name-'+service,name,0,48,240,28,text+'fontStyle=1;fontSize=20;','container-'+service)
        ports=v.get('ports',[])
        if ports:
            pairs=[]
            for p in ports:
                # Resolve the declared Grafana default without splitting its variable syntax.
                p=p.replace('${GRAFANA_PORT:-13001}','13001')
                parts=p.split(':');pairs.append(parts[-2]+':'+parts[-1])
            port=' | '.join(pairs)
        else:
            port={'product-view-mongo':'TCP 27017 — no host port','keycloak-db':'TCP 5432 — no host port','confirmation-job':'no host port','alloy':'no host port'}[service]
        box('ports-'+service,port,0,78,240,32,text+'fontSize=18;','container-'+service)

    node('frontend',50,230);node('apigateway',300,230)
    for i,(api,db,net) in enumerate(owners):
        node(api,1010,400+i*132);node(db,1450,400+i*132)
    ancillary=['rabbitmq','product-synchronizer','confirmation-job','grafana','loki','alloy','kubernetes-gui','prometheus']
    for i,service in enumerate(ancillary):
        node(service,330+(i%2)*300,430+(i//2)*225)
    boundary('edge-repeat','',620,1080,285,140,'#577fb5')
    label('edge-repeat-label','also aj-edge',635,1076,260,25,18)
    boundary('admin','Local database administration — explicit network membership',20,1230,1730,190,'#6b7280')
    node('database-gui',60,1270);node('mongo-gui',950,1270)
    label('admin-networks','Adminer joins: users-data, products-data, orders-data, payments-data, identity-data',320,1280,580,75,19)
    label('mongo-networks','Mongo Express joins product-view-data',1210,1290,490,55,20)
    label('legend','Blue/red/orange = Docker networks; green = host loopback; grey = administration. All 24 containers are shown.',30,1430,1720,32,21)
    label('legend2','Orange lanes extend into aj-backend: owner APIs join both networks; databases do not join backend.',30,1465,1720,32,21)
    label('legend3','Named volumes persist databases, RabbitMQ and monitoring data. Headlamp views a separate Kubernetes control plane.',30,1500,1720,32,20)

    def edge(id,src,dst,t='',style=''):
        c=E.SubElement(r,'mxCell',{'id':id,'value':t,'style':'edgeStyle=orthogonalEdgeStyle;rounded=0;html=1;endArrow=block;strokeColor=#333333;fontSize=18;labelBackgroundColor=#ffffff;'+style,'edge':'1','parent':'1','source':src,'target':dst})
        E.SubElement(c,'mxGeometry',{'relative':'1','as':'geometry'})

    edge('frontend-gateway','container-frontend','container-apigateway','HTTP','exitX=1;exitY=0.5;entryX=0;entryY=0.5;')
    edge('frontend-published','container-frontend','host','','exitX=0.5;exitY=0;entryX=0.0867;entryY=1;')
    for api,db,net in owners:
        edge('db-link-'+api,'container-'+api,'container-'+db,'TCP '+('27017' if api=='product-view' else '5432'),'exitX=1;exitY=0.5;entryX=0;entryY=0.5;')
    assert seen==set(cfg['services']),set(cfg['services'])-seen
    out=E.Element('mxfile',{'host':'app.diagrams.net'})
    d=E.SubElement(out,'diagram',{'id':'compose-network-view','name':'Docker Compose networks'});d.append(m)
    E.ElementTree(out).write(ROOT/'documentation/report-figures/docker-compose-network.drawio',encoding='utf-8',xml_declaration=True)
    print('Rendered all 24 containers with source-derived port mappings.')


if __name__=='__main__':
    main()
