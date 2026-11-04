"""Generate reviewable standard contracts. --check detects stale generated files."""
import copy
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
STRING = {'type': 'string'}
GUID = {'type': 'string', 'format': 'uuid'}
MONEY = {'type': 'number', 'minimum': 0, 'maximum': 9999999999.99, 'multipleOf': .01}
LOCAL_TIME = {'type': 'string', 'description': 'ISO local date-time interpreted as UTC; no offset suffix'}


def nullable(value):
    return {'anyOf': [copy.deepcopy(value), {'type': 'null'}]}


def obj(fields, required=None):
    return {'type': 'object', 'properties': fields, 'required': list(fields) if required is None else required}


def enum(*values):
    return {'type': 'string', 'enum': list(values)}


def array(value):
    return {'type': 'array', 'items': value}


user_write = obj({key: STRING for key in ['firstName', 'lastName', 'email', 'phoneNumber', 'address', 'postalCode', 'city']}, ['firstName', 'lastName', 'email'])
user = obj({'guid': GUID, **user_write['properties']})
for key in ['phoneNumber','address','postalCode','city']: user['properties'][key]=nullable(STRING)
product_fields = {key: STRING for key in ['brand', 'model', 'description', 'currency', 'color', 'fuelType', 'transmission']}
product_fields.update({'year': {'type': 'integer', 'minimum': 1886}, 'price': MONEY,
    'mileage': {'type': 'integer', 'minimum': 0}, 'status': enum('AVAILABLE', 'UNAVAILABLE', 'RESERVED', 'SOLD'),
    'equipment': array(STRING), 'images': array(STRING),
    'engine': obj({'type': STRING, 'powerHp': {'type': 'integer'}, 'capacityLiters': {'type': 'number'}}, []),
    'performance': obj({'topSpeedKmh': {'type': 'integer'}, 'zeroTo100': {'type': 'number'}}, []),
    'dimensions': obj({key: {'type': 'integer'} for key in ['length', 'width', 'height']}, [])})
product = obj({'guid': GUID, **product_fields, 'version': {'type': 'integer', 'minimum': 0},
               'createdAt': STRING, 'updatedAt': STRING})
product_write = obj(copy.deepcopy(product_fields), ['brand', 'model', 'price'])
product_write['properties']['status'] = enum('AVAILABLE', 'UNAVAILABLE')
order_create = obj({'userGuid': GUID, 'productGuid': GUID, 'shippingAddress': STRING, 'notes': STRING}, ['userGuid', 'productGuid', 'shippingAddress'])
order = obj({'guid': GUID, **order_create['properties'],
    'status': enum('PENDING', 'RESERVING', 'RESERVED', 'PAYMENT_PENDING', 'REVIEW_REQUIRED', 'COMPLETED', 'FAILED', 'REJECTED', 'CANCELLED'),
    'totalAmount': nullable(MONEY), 'currency': nullable(STRING), 'createdAt': LOCAL_TIME,
    'updatedAt': LOCAL_TIME, 'expiresAt': nullable(LOCAL_TIME)})
payment_create = obj({'orderGuid': GUID, 'paymentMethod': enum('MANUAL', 'BANK_TRANSFER', 'CASH')}, ['orderGuid'])
payment = obj({'guid': GUID, **payment_create['properties'], 'amount': nullable(MONEY), 'currency': nullable(STRING),
    'status': enum('REQUESTED', 'PENDING', 'REJECTED', 'COMPLETED', 'FAILED'), 'transactionReference': STRING,
    'paidAt': nullable(LOCAL_TIME), 'createdAt': LOCAL_TIME})
error = obj({'status': {'type': 'integer'}, 'service': STRING, 'message': STRING, 'timestamp': {'type': 'string', 'format': 'date-time'}})
health = obj({'service': STRING, 'status': enum('UP', 'DOWN')})
history = array(obj({'from': nullable(STRING), 'to': STRING, 'at': STRING}))
specs = {}


def spec(name):
    return {'openapi': '3.1.0', 'info': {'title': 'AJ Motors '+name, 'version': '1.0.0'},
        'servers': [{'url': 'http://localhost:3000'}], 'paths': {},
        'components': {'securitySchemes': {'bearer': {'type': 'http', 'scheme': 'bearer', 'bearerFormat': 'JWT'},
            'projection': {'type': 'apiKey', 'in': 'header', 'name': 'X-Projection-Token'},
            'confirmation': {'type': 'apiKey', 'in': 'header', 'name': 'X-Confirmation-Token'}}}}


def operation(document, path, method, response=None, request=None, status='200', security='bearer', description=''):
    op = {'operationId': method+'_'+path.replace('/', '_').replace('{', '').replace('}', ''),
        'description': description, 'security': [] if security is None else [{security: []}],
        'responses': {status: {'description': 'Success'}}}
    if response is not None:
        op['responses'][status]['content'] = {'application/json': {'schema': response}}
    for code in ['400', '401', '403', '404', '409', '503']:
        op['responses'][code] = {'description': {'400':'Invalid input','401':'Missing or invalid token','403':'Forbidden','404':'Not found','409':'Conflict','503':'Unavailable'}[code],
                                 'content': {'application/json': {'schema': error}}}
    params = []
    for segment in path.split('/'):
        if segment.startswith('{'):
            params.append({'in':'path', 'name':segment[1:-1], 'required':True, 'schema':GUID})
    if method == 'post' and path in ['/api/v1/'+x for x in ['users','products','orders','payments']]:
        params.append({'in':'header','name':'Idempotency-Key','required':False,'schema':{'type':'string','pattern':'^[A-Za-z0-9_-]{1,64}$'},
                       'description':'Scoped to identity and endpoint; identical retry replays 201; changed body gives 409'})
    if params:
        op['parameters'] = params
    if request is not None:
        op['requestBody'] = {'required':True, 'content':{'application/json':{'schema':request}}}
    document['paths'].setdefault(path,{})[method] = op
    return op


for name in ['users','products','orders','payments','product-view','product-synchronizer','apigateway','frontend']:
    d = spec(name)
    operation(d, '/health/live', 'get', health, security=None)
    op = operation(d, '/health', 'get', health, security=None)
    op['responses']['503']['content']['application/json']['schema'] = health
    metrics = operation(d, '/metrics', 'get', security=None, description='Internal only; never forwarded by gateway')
    metrics['responses']['200']['content'] = {'text/plain': {'schema': STRING}}
    specs[name] = d

for name, write, read, identifier in [('users',user_write,user,'userGuid'),('products',product_write,product,'productGuid'),('orders',order_create,order,'orderGuid'),('payments',payment_create,payment,'paymentGuid')]:
    d=specs[name]; path='/api/v1/'+name
    operation(d,path,'post',read,write,'201',description='Accepted local resource; order/payment processing completes asynchronously')
    if name=='payments':
        d['paths'][path]['post']['responses']['200']={'description':'Existing payment','content':{'application/json':{'schema':read}}}
    operation(d,path,'get',array(read),security=None if name=='products' else 'bearer')
    operation(d,path+'/{'+identifier+'}','get',read,security=None if name=='products' else 'bearer')
    update = write
    if name=='orders': update=obj({'shippingAddress':STRING,'notes':STRING,'status':enum('CANCELLED')},[])
    if name=='payments': update=obj({'status':enum('COMPLETED','FAILED')})
    updated = obj({'guid':GUID,'updatedAt':STRING}) if name=='users' else read
    operation(d,path+'/{'+identifier+'}','put',updated,update)
    if name!='payments': operation(d,path+'/{'+identifier+'}','delete',status='204')

operation(specs['users'],'/api/v1/users/me','post',user,description='Ensure own customer profile using verified identity; never links by email')
operation(specs['orders'],'/api/v1/users/{userGuid}/orders','get',array(order))
operation(specs['orders'],'/api/v1/orders/{orderGuid}/history','get',history)
pdf=operation(specs['orders'],'/api/v1/orders/{orderGuid}/confirmation','get')
pdf['responses']['200']['content']={'application/pdf':{'schema':{'type':'string','format':'binary'}}}
internal=operation(specs['orders'],'/internal/orders/{orderGuid}/confirmation','put',status='204',security='confirmation',description='Internal job writer; first document wins; no public proxy route')
internal['requestBody']={'required':True,'content':{'application/pdf':{'schema':{'type':'string','format':'binary'}}}}
operation(specs['payments'],'/api/v1/orders/{orderGuid}/payments','get',array(payment))
operation(specs['products'],'/api/v1/products/description-draft','post',obj({'description':STRING,'model':STRING,'requiresApproval':{'const':True}}),obj({'brand':STRING,'model':STRING,'year':{'type':'integer'}}))
for path,value in [('/api/v1/products',array(product)),('/api/v1/products/{productGuid}',product)]:operation(specs['product-view'],path,'get',value,security=None)
operation(specs['product-view'],'/api/v1/graphql','post',obj({'data':{'type':['object','null']},'errors':array({'type':'object'})},[]),obj({'query':STRING,'variables':{'type':'object'},'operationName':STRING},['query']),security=None)
operation(specs['product-view'],'/internal/projections/products/{productGuid}','put',request=obj({'eventId':GUID,'topic':enum('product.created','product.updated','product.reserved','product.removed'),'snapshot':product}),status='204',security='projection')
operation(specs['product-view'],'/internal/projections/health','get',health,security='projection')
operation(specs['apigateway'],'/api/v1/auth/session','get',obj({'subject':STRING,'username':nullable(STRING),'name':nullable(STRING),'customerGuid':nullable(GUID),'roles':array(STRING)}))
operation(specs['apigateway'],'/api/v1/status','get',obj({'status':enum('UP','DEGRADED'),'services':array(health),'message':STRING}),security=None)
operation(specs['frontend'],'/auth/config','get',obj({'url':STRING,'realm':STRING,'clientId':STRING}),security=None)
# Proxy contracts expose the union of public interfaces, preserving each backend's security.
for name in ['users','products','orders','payments','product-view']:
    for path,ops in specs[name]['paths'].items():
        if path.startswith('/api/v1/'):
            if name=='product-view' and path.startswith('/api/v1/products'):
                specs['apigateway']['paths'].setdefault(path,{}).update(copy.deepcopy(ops))
            else: specs['apigateway']['paths'].setdefault(path,{}).update(copy.deepcopy(ops))
for path,ops in specs['apigateway']['paths'].items():
    if path.startswith('/api/v1/'):specs['frontend']['paths'][path]=copy.deepcopy(ops)

reservation=obj({'orderGuid':GUID,'userGuid':GUID,'productGuid':GUID,'totalAmount':nullable(MONEY),'currency':nullable(STRING)})
payment_ref=obj({'orderGuid':GUID,'paymentGuid':GUID})
outcome=obj({**payment_ref['properties'],'productGuid':GUID})
invoice=obj({**reservation['properties'],'paymentGuid':GUID})
messages={}
for topic in ['customer.check','customer.valid','customer.invalid','quantitycheck','quantityreserved','quantityrejected','order.cancelled']:messages[topic]=reservation
for topic in ['payment.requested','payment.rejected']:messages[topic]=payment_ref
for topic in ['payment.completed','paymentfail']:messages[topic]=outcome
for topic in ['payment.authorized','payment.reconcile','order.completed']:messages[topic]=invoice
messages['order.owner']=obj({'orderGuid':GUID,'userGuid':GUID})
for topic in ['product.created','product.updated','product.reserved','product.removed']:messages[topic]=product
asyncapi={'asyncapi':'2.6.0','info':{'title':'AJ Motors events','version':'1.0.0'},
    'servers':{'local':{'url':'localhost:5672','protocol':'amqp','description':'Local development; credentials through environment'}},'channels':{}}
for topic,payload in messages.items():
    envelope=obj({'eventId':GUID,'eventType':enum(topic),'occurredAt':{'type':'string','format':'date-time'},
        'correlationId':GUID,'schemaVersion':{'type':'integer','enum':[1]},'payload':payload})
    asyncapi['channels'][topic]={'description':'Routing key on aj.events; persistent message; manual ack after commit',
        'bindings':{'amqp':{'is':'routingKey','exchange':{'name':'aj.events','type':'topic','durable':True,'autoDelete':False},'bindingVersion':'0.3.0'}},
        'subscribe':{'message':{'name':topic.replace('.','_'),'contentType':'application/json','payload':envelope,
            'correlationId':{'location':'$message.payload#/correlationId'}}}}


def output(path,value):
    text=json.dumps(value,indent=2,ensure_ascii=False)+'\n'
    if '--check' in sys.argv:
        assert path.exists() and path.read_text(encoding='utf-8')==text, 'Stale generated contract: '+str(path)
    else:
        path.parent.mkdir(parents=True,exist_ok=True);path.write_text(text,encoding='utf-8')


for name,d in specs.items():
    for prefix in ['documentation/contracts','frontend/src/main/resources/public/contracts']:
        output(ROOT/prefix/'openapi'/f'{name}.json',d)
for prefix in ['documentation/contracts','frontend/src/main/resources/public/contracts']:
    output(ROOT/prefix/'asyncapi.json',asyncapi)
print('Generated contracts match source definitions.' if '--check' in sys.argv else 'Generated OpenAPI/AsyncAPI contracts.')
