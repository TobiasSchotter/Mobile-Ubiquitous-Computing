from tkinter import *

import time
import paho.mqtt.client as mqtt


canvas_width = 500
canvas_height = 500


master = Tk()
master.title( "MQTT Steuerung" )
w = Canvas(master, 
           width=canvas_width, 
           height=canvas_height)
w.pack(expand = YES, fill = BOTH)

#Unteres Label
message = Label( master, text = "Subscribed to mqtt/message" )
message.pack( side = BOTTOM )
#Punkt anfangs zentrieren
char = w.create_oval(canvas_width/2,canvas_height/2,canvas_width/2-20,canvas_height/2-20,fill="red")


Broker = "127.0.0.1"
sub_topic = "mqtt/message"  # receive messages on this topic
pub_topic = "sensor/data"       # send messages to this topic


# when connecting to mqtt do this;

def on_connect(client, userdata, flags, rc):
    print("Connected with result code "+str(rc))
    client.subscribe(sub_topic)
    print("Subscribed to " + sub_topic)

# when receiving a mqtt message do this;

def on_message(client, userdata, msg):
    message = str(msg.payload, 'utf-8')
    print(msg.topic + " " + message)
    

def on_publish(mosq, obj, mid):
    print("mid: " + str(mid))

client = mqtt.Client()
client.on_connect = on_connect
client.on_message = on_message
client.connect(Broker, 1883, 60)
client.loop_start()


def follow_mouse( cursor ):
   x, y = cursor.x, cursor.y
   w.coords(char, x - 10, y - 10, x + 10, y + 10)
   #umwandlung der Koordinaten in einen -9.81 - 9.81 Intervall 
   x = (x - (canvas_width/2)) / ((canvas_width/2)/9.81);
   y = (y -(canvas_height/2))/ ((canvas_width/2)/9.81);

   #publish an sensor/data der x und y Werte
   client.publish(pub_topic, str(x) + "," + str(y))
   

w.bind( "<Motion>", follow_mouse )
mainloop()
