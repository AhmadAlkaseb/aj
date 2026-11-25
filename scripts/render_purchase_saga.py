"""Draw the implemented saga's success, compensation and ambiguous-payment paths."""
from pathlib import Path
import xml.etree.ElementTree as E

ROOT=Path(__file__).resolve().parent.parent


def main():
    out=E.Element('mxfile',{'host':'app.diagrams.net'})
    page=E.SubElement(out,'diagram',{'name':'Reservation and compensation','id':'purchase-saga'})
    model=E.SubElement(page,'mxGraphModel',{'page':'0','background':'#ffffff'})
    root=E.SubElement(model,'root')
    E.SubElement(root,'mxCell',{'id':'0'});E.SubElement(root,'mxCell',{'id':'1','parent':'0'})
    blue=('#edf4ff','#4874ac');green=('#e9f5ec','#388052');red=('#fff0ef','#b94b43');amber=('#fff7df','#b6852a')

    def node(id,title,body,x,y,w=240,h=100,palette=blue,diamond=False):
        fill,stroke=palette
        label=f'<b>{title}</b><br>{body}'
        style=f'rounded=1;arcSize=14;whiteSpace=wrap;html=1;fillColor={fill};strokeColor={stroke};strokeWidth=2;fontColor=#203047;fontFamily=Arial;fontSize=17;spacing=6;verticalAlign=middle;'
        if diamond:style+='shape=rhombus;rounded=0;'
        c=E.SubElement(root,'mxCell',{'id':id,'value':label,'style':style,'vertex':'1','parent':'1'})
        E.SubElement(c,'mxGeometry',{'x':str(x),'y':str(y),'width':str(w),'height':str(h),'as':'geometry'})

    def text(id,value,x,y,w,h,size=22):
        c=E.SubElement(root,'mxCell',{'id':id,'value':value,'style':f'text;whiteSpace=wrap;html=1;fontFamily=Arial;fontSize={size};fontColor=#42526b;align=center;','vertex':'1','parent':'1'})
        E.SubElement(c,'mxGeometry',{'x':str(x),'y':str(y),'width':str(w),'height':str(h),'as':'geometry'})

    def edge(id,source,target,label='',color='#4874ac',ports='',points=(),dashed=False):
        style=f'edgeStyle=orthogonalEdgeStyle;rounded=1;arcSize=8;html=1;endArrow=block;endFill=1;strokeWidth=2;strokeColor={color};fontColor={color};fontFamily=Arial;fontSize=18;labelBackgroundColor=#ffffff;'+ports
        if dashed:style+='dashed=1;'
        c=E.SubElement(root,'mxCell',{'id':id,'value':label,'style':style,'edge':'1','parent':'1','source':source,'target':target})
        g=E.SubElement(c,'mxGeometry',{'relative':'1','as':'geometry'})
        if points:
            a=E.SubElement(g,'Array',{'as':'points'})
            for x,y in points:E.SubElement(a,'mxPoint',{'x':str(x),'y':str(y)})

    text('title','Reservation and payment saga',40,0,1400,42,32)
    node('available','Car AVAILABLE','Can be reserved again',340,60,240,65,green)
    node('customer','1. Check customer','Users validates customer<br>Order: PENDING',40,200)
    node('reserve','2. Reserve car','Products locks this order’s car<br>Order: RESERVED',340,200)
    node('claim','3. Authorise payment','Orders claims reservation<br>Order: PAYMENT_PENDING',640,200)
    node('outcome','Payment','outcome?',960,200,160,100,blue,True)
    node('completed','Purchase completed','payment.completed<br>Order: COMPLETED<br>Car: SOLD',1200,200,240,100,green)
    node('rejected','Order REJECTED','Invalid customer or car unavailable<br>No reservation to undo',40,420,240,90,red)
    node('cancelled','Order CANCELLED','Cancel / unpaid reservation expiry<br>order.cancelled',650,350,240,90,red)
    node('failed','Payment failed','Payments emits paymentfail<br>Orders: FAILED',940,470,240,90,red)
    node('release','Undo the reservation','Products releases this order’s car<br>RESERVED → AVAILABLE',340,470,300,90,red)
    node('review','Outcome unknown','After payment deadline:<br>REVIEW_REQUIRED<br>Keep reservation; reconcile',1200,470,240,110,amber)
    edge('validated','customer','reserve',ports='exitX=1;exitY=0.5;entryX=0;entryY=0.5;')
    edge('stock','available','reserve',color='#388052',ports='exitX=0.5;exitY=1;entryX=0.5;entryY=0;')
    edge('reserved','reserve','claim',ports='exitX=1;exitY=0.5;entryX=0;entryY=0.5;')
    edge('authorised','claim','outcome',ports='exitX=1;exitY=0.5;entryX=0;entryY=0.5;')
    edge('paid','outcome','completed','Success','#388052','exitX=1;exitY=0.5;entryX=0;entryY=0.5;')
    edge('bad-customer','customer','rejected','customer.invalid','#b94b43','exitX=0.5;exitY=1;entryX=0.5;entryY=0;')
    edge('no-stock','reserve','rejected','quantityrejected','#b94b43','exitX=0;exitY=0.7;entryX=1;entryY=0.5;',[(310,270),(310,465)])
    edge('cancel','reserve','cancelled',color='#b94b43',ports='exitX=0.5;exitY=1;entryX=0;entryY=0.5;',points=[(460,330),(610,330),(610,395)])
    edge('cancel-release','cancelled','release',color='#b94b43',ports='exitX=0.5;exitY=1;entryX=0.87;entryY=0;',points=[(770,450),(601,450)])
    edge('payment-failed','outcome','failed','Failure','#b94b43','exitX=0.3;exitY=1;entryX=0.5;entryY=0;',[(1008,440),(1060,440)])
    edge('compensate','failed','release','Same event to Products','#b94b43','exitX=0;exitY=0.5;entryX=1;entryY=0.5;')
    edge('available-again','release','available',color='#b94b43',ports='exitX=0.5;exitY=1;entryX=0;entryY=0.5;',points=[(490,610),(10,610),(10,92)])
    edge('uncertain','outcome','review','No confirmed outcome','#b6852a','exitX=0.8;exitY=1;entryX=0.5;entryY=0;',[(1088,390),(1320,390)])
    edge('reconcile','review','outcome','Reconcile / retry','#b6852a','exitX=1;exitY=0.5;entryX=0.5;entryY=0;',[(1490,525),(1490,160),(1040,160)],True)
    text('legend','Blue: normal flow   •   Green: success / available inventory   •   Red: rejection or compensation   •   Amber: wait and reconcile',40,645,1400,35,20)
    text('note','Each service commits locally. Compensation releases inventory; it does not erase the failed order or restart it.',40,683,1400,35,20)
    E.indent(out)
    E.ElementTree(out).write(ROOT/'documentation/report-figures/purchase-saga-detailed.drawio',encoding='utf-8',xml_declaration=True)


if __name__=='__main__':main()
