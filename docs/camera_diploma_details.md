TODO:
- DIPLOMA overview (introduces region leader relaying/hopping, consistent shared memory among region, heartbeats/talking to cloud detailed enough for the trials section, or write it along with the trials section)
- UI with explaination of the app screen shots
- Camera_CLOUD
- Trials

Order:
X1. Camera_DIPLOMA take
X2. Camera_CLOUD
3. Trials + heartbeat/talking section of DIPLOMA overview
4. screen shot DDSM / UI writing

Camera_DIPLOMA app details
--------------------------

The Camera_DIPLOMA app utilizes the DIPLOMA for inter-region communication, but has a custom intra-region communication between a region's leader and nonleader as well as tasks carried out at remote region's leaders. Figure # categorizes all the java file that Camera_DIPLOMA app uses by assigning each file as DIPLOMA or app-specific.

Figure #
-------------------------
|   File Name                           DIPLOMA or app*     lines**     Description
-------------------------
|   Atom.java                           DIPLOMA             100         DIPLOMA inter-region communication request or reply (leader 1 <---> leader 2)
|   CameraSurfaceView.java              app                 100         camera UI
|   Cloud.java                          DIPLOMA             150         HTTP requests to the cloud to keep states consistent
|   DSMLayer.java                       DIPLOMA             500         ????? TODO
|   DSMUser.java                        DIPLOMA              10         ????? TODO
|   GetPhotoInfo.java                   app                  20         packet inside Packet that congregates photo-request information to persist through all legs of the requestA
|   Globals.java                        app                  70         global variables 
|   HysteresisSpinnerListener.java      app                  20         UI, to set width of hysteresis in real-life deployment
|   Mux.java                            DIPLOMA/app         300         sort requests heard
|   NetworkThread.java                  DIPLOMA             200         DIPLOMA sending requests
|   Packet.java                         DIPLOMA/app          80         Wifi packet for intra-region communication (nonleader <---> leader)
|   RegionKey.java                      DIPLOMA              50         Region key class
|   *StatusActivity.java*               app                1200         Where the app starts. UI of app. Run on client-side of app (sends requests and hear leader responses).***
|   *UserApp.java*                      app                 550         Run on leader-side of app, have a local-leader section and a remote-leader section 
|   VCoreDaemon.java                    DIPLOMA            1000         Keeping DIPLOMA alive and consistent through heartbeats, state-switches, and movements of phones
|------------------
|   Total                                                  4400
|------------------
    * DIPLOMA/app means the file is mainly DIPLOMA, but modified for the app
    ** Rounded
    *** Leader phones also have a client side because they have to make requests too, so StatusActivity is run on all phones.

To read more about DIPLOMA, please refer to [TODO: insert Jason's paper]. The most important app files are StatusActivity.java and UserApp.java. 

StatusActivity.java contains three important components: onCreate(), button onClickListeners, and its message handler. The one and only activity of the app is initialized in the onCreate() function of StatusActivity.java. Inside onCreate(), UI elements are linked to their click listeners and the Mux is started in order to sort requests that the phone hears (Mux is described in detail later). The various onClickListeners can be viewed as the "client-side" of the app, generating different requests to be sent through DIPLOMA. The requests' replies are processed inside the message handler, the last important component of StatusActivity.java.

UserApp.java can be viewed as the "server-side" of the app, running only on region leader phones. Different legs of the request activate different functions inside UserApp. These functions as well as the crucial functions StatusActivity are best understood through an example, walking through chronologically a scenario of a remote region _get_.

#########################################################
#########################################################


Camera_DIPLOMA Scenario 1. Non-leader of region 1 taking a photo
------
Phone A: request originator, in this scenario a non-leader of region 1
Phone B: leader of Phone A's region, in this scenario, region 1's leader

Phone A:
1. User presses the "Take Photo" button on Phone A. 
2. StatusActivity.java: my_camera_button OnClickListener() 
        registers the button press and disable all other button presses with ProgressDialog until response is received in step 28.
3. StatusActivity.java: HandlePictureStorage() gets the byte array of the photo, compresses it, set it into a Wifi packet (First-leg Wifi packet) that is broadcasted in the local region (1), which should be picked up by the local region leader. 
4.      The Wifi packet is saved to the global `requestPacket` 
5. StatusActivity.java: sendRequestPacketRepeatingRunnable sends `requestPacket` to mux 4 times (4 = 1 + sendingRequestsTimeoutPeriod/sendingRequestsPeriod) or StatusActivity's handler hears a first-leg ack from the local leader. The repetition increases the chance of success when Wifi is spotty. Right before the Wifi packet is sent, the time is recoreded as the start time for the request latency calculation.

* First Leg: *      Phone A --- Wifi ---> Phone B
                 originator --- Wifi ---> local leader
                            First-leg Wifi packet:
                            - type: Packet.CLIENT_REQUEST
                            - subtype: Packet.CLIENT_UPLOAD_PHOTO
                            - a unique requestCounter based on the IP address (mId) of Phone A
                            - a byte stream of a GetPhotoInfo object for tracking, throughout different stages of the request
                              GetPhotoInfo object:
                                - originNodeId: the IP address (mId) of Phone A
                                - srcRegion: source region of Phone A (1)
                                - destRegion: destination region, same as source (1))
                                - requestCtr: the unique requestCounter 
                                - photoBytes: set to bytes of picture

Phone B:
5. Mux.java processMessage() hears the message, figures out that it has `case Packet.CLIENT_REQUEST`, and forwards the package to UserApp's handleClientRequest.
6. UserApp.java: handleClientRequest()
        is used by a leader to handle the first-leg of the request initiated by one of its non-leaders. First, handleClientRequest() makes sure that the Wifi packet is in fact from its own region and then it makes sure that the requestCounter of the Wifi packet has not been encountered before.
7.      After making sure that the Wifi packet is new, different actions are taken based on the subtype of the Wifi packet. 
8.      In our case of Packet.CLIENT_UPLOAD_PHOTO, a DIPLOMA atom request, with procedure `SERVER_UPLOAD_PHOTO`, is made and sent to the destination region, which is itself, through DIPLOMA. The atom request also contains the unmodified GetPhotoInfo bytes from the Wifi packet.
9.      A first-leg ack is sent to Phone A through a new Wifi packet. If this ack is lost or if the first leg failed, Phone A's StatusActivity.java will repeatly send the first leg Wifi packet 4 times (see step 5) or until its StatusActivity hears this first-leg ack.
            First-leg ack Wifi packet:
                - type: Packet.SERVER_REPLY
                - subtype: Packet.SERVER_FIRST_LEG_ACK

* Second Leg: *                           Phone B --- DIPLOMA ---> Phone B
                                     local leader --- DIPLOMA ---> remote leader = local leader
                                                  Second-leg DIPLOMA atom request:
                                                  - procedure: SERVER_UPLOAD_PHOTO
                                                  - GetPhotoInfo object
                                                        - same as First-leg Wifi packet's GetPhotoInfo object

Phone B:
10. Through DIPLOMA, the atom request is sent to Phone B itself.
11. UserApp.java: handleDSMRequest()
        executes the user request. Different tasks are performed depending on what the atom request procedure is. A user request of getting photos correspond to the SERVER_UPLOAD_PHOTO procedure.
12.     Inside the case SERVER_UPOLAD_PHOTO, Phone B retrieves the photo byte array from the DIPLOMA atom request GetPhotoInfo's photoBytes. This array is then saved into the region's consistent shared memory: blocks.lines (see DSMLayer.java for details). Since block.lines is a hash map, all photo data are grouped under the same key, `Globals.PHOTO_KEY`. This key then points to the ArrayList of photos, where we get the newest photo. Currently there is only one element in the ArrayList that is constantly updated to contain the newest photo of the region.  
        Since only the newest photo of the region is ever retrieved, it might seem strange that only the first element of the photo Arraylist is updated. Initially, the app's UI was designed to be able to retrieve the ith newest photo. So it is indeed superfluous to store the newest photo's byte array in an Arraylist, but this design allows a simpler change if in the future the app should save multiple photos.
13.     The leader displays this newly saved photo on the UI through a SERVER_SHOW_NEWPHOTO packet.
14.     GetPhotoInfo bytes will be sent back in DIPLOMA to the local leader (itself) and the client side (StatusActivity) of the originator phone. Since photoBytes are no longer needed in StatusActivity, it is set to null to decrease the size of the DIPLOMA atom reply.
15.     A DIPLOMA atom reply is made with the same procedure as the atom request (`SERVER_UPLOAD_PHOTO`), but reversed destination and source regions. The atom reply also contains the modified version of the atom request's GetPhotoInfo, with photoBytes set to null. This atom reply is send from the remote leader back to the local leader, the same phone, through DIPLOMA, as the third leg of the request.

* Third Leg: *                           Phone B <--- DIPLOMA --- Phone B
                                    local leader <--- DIPLOMA --- remote leader = local leader
                                                  Third-leg DIPLOMA atom reply:
                                                  - procedure: SERVER_GET_PHOTO
                                                  - GetPhotoInfo object:
                                                        - same information as First-leg Wifi packet's GetPhotoInfo object
                                                        - photoBytes: null
                                                        - isSuccess: true for successful save

Phone B:
16. UserApp.java: handleDSMReply() of the local leader (Phone B)
        processes the Atom reply from the the remote leader (Phone B) to figure out a) the IP address of the original phone and b) whether the atom request timed out (even though it's from the same phone, the function is written generically that it works both for GET and TAKE)
17.     a) The original phone's IP address is extracted from the atom reply GetPhotoInfo object's originNodeId field. The IP address is required to send a final Wifi packet back to the originator phone node. This Wifi packet contains the same GetPhotoInfo objects as the atom reply with the addition of the isSuccess field set to indicate whether the atom request timed out.
18.     b) An atom request times out if the local leader (Phone B) cannot reach the remote leader (Phone C) with DIPLOMA, in which case the local leader will send a fake self reply with the reply atom's `timedOut` field set to `true` (see DSMLayer.java). (This timed out case is not represented by the above Third Leg diagram, the DIPLOMA atom reply would be a self-loop on the local leader instead.) When a reply is detected to be timedOut inside handleDSMReply(), the GetPhotoInfo object's isSuccess is set to false.
19.     A reply counter is constructed based on the request counter to keep track of all the Wifi reply packets.
20.     From the procedure of the atom reply, handleDSMReply() sets subtype of the final Wifi reply packet.
21.     The Wifi reply is put into a hash map, `replyPacketMap`, to repeatedly send a reply until the originator acknowledges its acceptance.
22.     The Wifi reply is send through the `sendReplies` function.
23. UserApp.java: sendReplies() is a function to send Wifi replies every sendingRepliesPeriod until Phone B receives the final leg ack from the client or the sendingRepliesTimeoutPeriod is reached. 
24.     The only parameter of `sendReplies()` is the reply counter, which is used to create two custom runnables, one for the reply, one for the reply timeout. The functions createReplyRepeatingRunnable() and create ReplyTimeoutR() are basically runnable wrappers to make the runnables take in different reply counter parameters.
25. UserApp.java: createReplyRepeatingRunnable() retrieves the Wifi packet from the hash map to finally send it. At the end of the function it posts itself to the handler to be called every sendingRepliesPeriod.

* Fourth Leg: *     Phone A <--- Wifi --- Phone B
                 originator <--- Wifi --- local leader
                            Fourth-leg/Final Wifi packet:
                            - type: Packet.SERVER_REPLY
                            - subtype: Packet.CLIENT_UPLOAD_PHOTO_ACK
                            - a unique requestCounter based on the IP address (mId) of Phone A
                            - a unique replyCounter based on requestCounter
                            - a byte stream of a GetPhotoInfo object for tracking, throughout different stages of the request
                              GetPhotoInfo object:
                                - same information as Third-leg DIPLOMA packet's GetPhotoInfo object
                                - isSuccess: false if DILPOMA level timed out

Phone A:
26. StatusActivity.java: myHandler handleMessage() 
        receives messages from its Mux and sorts the packets by its subtype.
27.     In our case of CLIENT_UPLOAD_PHOTO_ACK, first the current time is noted to calculate the latency by subtracting the start time from step 5.
28.     Remove the timeout runnable and enable button presses again.
29. StatusActivity.java: finalLegAck()
        The final leg ack is sent to Phone B, the local leader, through a new Wifi packet. If this ack is lost or if the first leg failed, Phone B's reply repeating runnable (step 25.) repeatly send the final Wifi packet until the sendingRepliesTimeoutPeriod is reached or it hears this final-leg ack. This final leg ack is sent even for duplicated Final Wifi packets received, such as in the case of a previously lost final leg ack.
            Final-leg ack Wifi packet:
                - type: Packet.CLIENT_REQUEST
                - subtype: Packet.CLIENT_FINAL_LEG_ACK
                - a unique requestCounter based on the IP address (mId) of Phone A
                - the replyCounter received from the Final Wifi packet
30. StatusActivity.java: myHandler handleMessage() case CLIENT_UPLOAD_PHOTO_ACK
        The packet reply counter is checked with an array list of processed reply counters. If found, that means the packet is a duplicate. Else, the reply counter is added to the array list and the packet proceeds to be processed.
31.     The GetPhotoInfo object from the packet contains the `isSuccess` field that tells whether the packet timed out at DIPLOMA or not. If so, we increment the counter for "bad" requests, `getBad`.
32.     As long as DIPLOMA did not time out, the counter for successful requests, `getGood`, is incremented.

Phone A does not receive reply from Phone B:
33. StatusActivity.java: buttonEnableProgressUploadTimeoutR
        There are times when the local leader is not available, possibly due to the leader switching regions but before a new leader is elected or sometimes the Wifi connection is not consistent. In such cases, the originator may never hear a reply from the local leader, i.e. Phone A never receives  the fourth and final leg Wifi Packet. Since UI is blocked as soon as buttons are pressed (step 2), this timeout is set up to enable the UI buttons again as well as record down the number of time outs in counter `getTimedout`.


#########################################################
#########################################################

Camera_DIPLOMA Scenario 2. Leader of region 1 take a photo
------
Phone B: request originator is the leader of region 1 in this scenario

The entire walkthrough is the same as Scenario 1, except "Phone A"'s StatusActivity roles are also run on Phone B, which is already performing the leader UserApp roles. See the following diagram:

                        Phone A/nonleader       Phone B/leader      Phone B/leader
                        StatusActivity          StatusActivity      UserApp
        Scenario 1          active                                      active
        Scenario 2                                  active              active

Wifi Packets are not sent over the Wifi but are conveniently treated as received packets in Mux.


#########################################################
#########################################################


Camera_DIPLOMA Scenario 3. Non-leader of region 1 getting region 5's photo. 
------
Phone A: request originator, in this scenario a non-leader of region 1
Phone B: leader of Phone A's region, in this scenario, region 1's leader
Phone C: leader of remote region, in this scenario, region 5's leader

Phone A:
1. User presses the "Get 5 Photo" button on Phone A. 
2. StatusActivity.java: get_button_listener OnClickListener() 
        registers the button press and knows the destination region is 5 and disable all other button presses with ProgressDialog until response is received in step 28.
3.      and creates a Wifi packet (First-leg Wifi packet) that is broadcasted in the local region (1), which should be picked up by the local region leader. 
4.      The Wifi packet is saved to the global `requestPacket` 
5. StatusActivity.java: sendRequestPacketRepeatingRunnable sends `requestPacket` to mux 4 times (4 = 1 + sendingRequestsTimeoutPeriod/sendingRequestsPeriod) or StatusActivity's handler hears a first-leg ack from the local leader. The repetition increases the chance of success when Wifi is spotty. Right before the Wifi packet is sent, the time is recoreded as the start time for the request latency calculation.

* First Leg: *      Phone A --- Wifi ---> Phone B
                 originator --- Wifi ---> local leader
                            First-leg Wifi packet:
                            - type: Packet.CLIENT_REQUEST
                            - subtype: Packet.CLIENT_DOWNLOAD_PHOTO
                            - a unique requestCounter based on the IP address (mId) of Phone A
                            - a byte stream of a GetPhotoInfo object for tracking, throughout different stages of the request
                              GetPhotoInfo object:
                                - originNodeId: the IP address (mId) of Phone A
                                - srcRegion: source region of Phone A (1)
                                - destRegion: destination region (5)
                                - requestCtr: the unique requestCounter 

Phone B:
5. Mux.java processMessage() hears the message, figures out that it has `case Packet.CLIENT_REQUEST`, and forwards the package to UserApp's handleClientRequest.
6. UserApp.java: handleClientRequest()
        is used by a leader to handle the first-leg of the request initiated by one of its non-leaders. First, handleClientRequest() makes sure that the Wifi packet is in fact from its own region and then it makes sure that the requestCounter of the Wifi packet has not been encountered before.
7.      After making sure that the Wifi packet is new, different actions are taken based on the subtype of the Wifi packet. In our case of Packet.CLIENT_DOWNLOAD_PHOTO, first the destination region of the request is retreived from the GetPhotoInfo bytes.
8.      Then a DIPLOMA atom request, with procedure `SERVER_GET_PHOTO`, is made and sent to the destination region (5). The atom request also contains the unmodified GetPhotoInfo bytes from the Wifi packet.
9.      A first-leg ack is sent to Phone A through a new Wifi packet. If this ack is lost or if the first leg failed, Phone A's StatusActivity.java will repeatly send the first leg Wifi packet 4 times (see step 5) or until its StatusActivity hears this first-leg ack.
            First-leg ack Wifi packet:
                - type: Packet.SERVER_REPLY
                - subtype: Packet.SERVER_FIRST_LEG_ACK

* Second Leg: *                           Phone B --- DIPLOMA ---> Phone C
                                     local leader --- DIPLOMA ---> remote leader
                                                  Second-leg DIPLOMA atom request:
                                                  - procedure: SERVER_GET_PHOTO
                                                  - GetPhotoInfo object
                                                        - same as First-leg Wifi packet's GetPhotoInfo object

Phone C:
10. Through DIPLOMA, the atom request is sent to Phone C, the leader of the destination region of the user request, which in this scenario is region 5. 
11. UserApp.java: handleDSMRequest()
        executes the user request. Different tasks are performed depending on what the atom request procedure is. A user request of getting photos correspond to the SERVER_GET_PHOTO procedure.
12.     Inside the case SERVER_GET_PHOTO, Phone C retrieves the photo byte array from its region's consistent shared memory: block.lines (see DSMLayer.java for details). Since block.lines is a hash map, all photo data are grouped under the same key, `Globals.PHOTO_KEY`. This key then points to the ArrayList of photos, where we get the newest photo. Currently there is only one element in the ArrayList that is constantly updated to contain the newest photo of the region.  
    TODO!!
    See XX for more details.. 
        Since only the newest photo of the region is retrieved, it might seem strange that only the first element of the photo Arraylist is updated. Initially, the app's UI was designed to be able to retrieve the ith newest photo. So it is indeed superfluous to store the newest photo's byte array in an Arraylist, but this design allows a simpler change if in the future the app should save multiple photos.
13.     Regardless of whether a photo exists in the remote region or not, the success flag, `isSuccess`, is set to true because the remote leader has successfully executed the request (as compared to the request timed out on the local leader, see step 33).
14.     If a photo exists, its data is set to GetPhotoInfo byte's photoBytes field. If there is no data info, the photoBytes filed is set to null.
15.     A DIPLOMA atom reply is made with the same procedure as the atom request (`SERVER_GET_PHOTO`), but reversed destination and source regions. The atom reply also contains the modified version of the atom request's GetPhotoInfo, with photoBytes set. This atom reply is send from the remote leader back to the local leader through DIPLOMA, as the third leg of the request.

* Third Leg: *                           Phone B <--- DIPLOMA --- Phone C
                                    local leader <--- DIPLOMA --- remote leader
                                                  Third-leg DIPLOMA atom reply:
                                                  - procedure: SERVER_GET_PHOTO
                                                  - GetPhotoInfo object:
                                                        - same information as First-leg Wifi packet's GetPhotoInfo object
                                                        - photoBytes: set to bytes of picture (or null if no picture in region)
                                                        - isSuccess: true for successful retrieval

Phone B:
16. UserApp.java: handleDSMReply() of the local leader (Phone B)
        processes the Atom reply from the the remote leader (Phone C) to figure out a) the IP address of the original phone and b) whether the atom request timed out
17.     a) The original phone's IP address is extracted from the atom reply GetPhotoInfo object's originNodeId field. The IP address is required to send a final Wifi packet back to the originator phone node. This Wifi packet contains the same GetPhotoInfo objects as the atom reply with the addition of the isSuccess field set to indicate whether the atom request timed out.
18.     b) An atom request times out if the local leader (Phone B) cannot reach the remote leader (Phone C) with DIPLOMA, in which case the local leader will send a fake self reply with the reply atom's `timedOut` field set to `true` (see DSMLayer.java). (This timed out case is not represented by the above Third Leg diagram, the DIPLOMA atom reply would be a self-loop on the local leader instead.) When a reply is detected to be timedOut inside handleDSMReply(), the GetPhotoInfo object's isSuccess is set to false.
19.     A reply counter is constructed based on the request counter to keep track of all the Wifi reply packets.
20.     From the procedure of the atom reply, handleDSMReply() sets subtype of the final Wifi reply packet.
21.     The Wifi reply is put into a hash map, `replyPacketMap`, to repeatedly send a reply until the originator acknowledges its acceptance.
22.     The Wifi reply is send through the `sendReplies` function.
23. UserApp.java: sendReplies() is a function to send Wifi replies every sendingRepliesPeriod until Phone B receives the final leg ack from the client or the sendingRepliesTimeoutPeriod is reached. 
24.     The only parameter of `sendReplies()` is the reply counter, which is used to create two custom runnables, one for the reply, one for the reply timeout. The functions createReplyRepeatingRunnable() and create ReplyTimeoutR() are basically runnable wrappers to make the runnables take in different reply counter parameters.
25. UserApp.java: createReplyRepeatingRunnable() retrieves the Wifi packet from the hash map to finally send it. At the end of the function it posts itself to the handler to be called every sendingRepliesPeriod.

* Fourth Leg: *     Phone A <--- Wifi --- Phone B
                 originator <--- Wifi --- local leader
                            Fourth-leg/Final Wifi packet:
                            - type: Packet.SERVER_REPLY
                            - subtype: Packet.CLIENT_SHOW_REMOTEPHOTO
                            - a unique requestCounter based on the IP address (mId) of Phone A
                            - a unique replyCounter based on requestCounter
                            - a byte stream of a GetPhotoInfo object for tracking, throughout different stages of the request
                              GetPhotoInfo object:
                                - same information as Third-leg DIPLOMA packet's GetPhotoInfo object
                                - isSuccess: false if DILPOMA level timed out

Phone A:
26. StatusActivity.java: myHandler handleMessage() 
        receives messages from its Mux and sorts the packets by its subtype.
27.     In our case of CLIENT_SHOW_REMOTEPHOTO, first the current time is noted to calculate the latency by subtracting the start time from step 5.
28.     Remove the timeout runnable and enable button presses again.
29. StatusActivity.java: finalLegAck()
        The final leg ack is sent to Phone B, the local leader, through a new Wifi packet. If this ack is lost or if the first leg failed, Phone B's reply repeating runnable (step 25.) repeatly send the final Wifi packet until the sendingRepliesTimeoutPeriod is reached or it hears this final-leg ack. This final leg ack is sent even for duplicated Final Wifi packets received, such as in the case of a previously lost final leg ack.
            Final-leg ack Wifi packet:
                - type: Packet.CLIENT_REQUEST
                - subtype: Packet.CLIENT_FINAL_LEG_ACK
                - a unique requestCounter based on the IP address (mId) of Phone A
                - the replyCounter received from the Final Wifi packet
30. StatusActivity.java: myHandler handleMessage() case CLIENT_SHOW_REMOTEPHOTO
        The packet reply counter is checked with an array list of processed reply counters. If found, that means the packet is a duplicate. Else, the reply counter is added to the array list and the packet proceeds to be processed.
31.     The GetPhotoINfo object from the packet contains the actual photo bytes of the remote photo. Its `isSuccess` field contains information on whether the packet timed out at DIPLOMA or not. If so, we increment the counter for "bad" requests, `getBad`.
32.     Regardless of the region having a photo or not, as long as DIPLOMA did not time out, the counter for successful requests, `getGood`, is incremented.

Phone A does not receive reply from Phone B:
33. StatusActivity.java: buttonEnableProgressDownloadTimeoutR
        There are times when the local leader is not available, possibly due to the leader switching regions but before a new leader is elected or sometimes the Wifi connection is not consistent. In such cases, the originator may never hear a reply from the local leader, i.e. Phone A never receives  the fourth and final leg Wifi Packet. Since UI is blocked as soon as buttons are pressed (step 2), this timeout is set up to enable the UI buttons again as well as record down the number of time outs in counter `getTimedout`.

#########################################################
#########################################################

Camera_DIPLOMA Scenario 4. Leader of region 1 getting region 5's photo. 
------
Phone B: request originator is the leader of region 1 in this scenario
Phone C: leader of remote region, in this scenario, region 5's leader

The entire walkthrough is the same as Scenario 3, except "Phone A"'s StatusActivity roles are also run on Phone B, which is already performing the leader UserApp roles. See Scenario 2's diagram.


#########################################################
#########################################################

Camera_CLOUD details
=================

Compared to Camera_DIPLOMA, Camera_CLOUD is a much simpler app.


-------------------------
|   File Name                           lines**     Description
-------------------------
|   CameraCloud.java                    1200        Where the app starts. UI of app. Connections to the cloud
|   CameraSurfaceView.java               100        Camera UI
|   Globals.java                          70        Global variables 
|   HysteresisSpinnerListener.java        20        UI, to set width of hysteresis in real-life deployment
|   RegionKey.java                        50        Region key class
|------------------
|   Total                               1400    
|------------------
 
Regions only nominally exist in Camera_CLOUD, each phone behaves independently. There are no leaders or nonleaders. Each TAKE and GET request is very similar, with just one roundtrip to the cloud server to save or retrieve information.

Scenario 1. Cloud Take
----------------------
1. User presses the "Take Photo" button on phone.
2. CameraCloud.java: my_camera_button OnClickListener() 
        registers the button press and disable all other button presses with ProgressDialog until response is received in step 10.
3. CameraCloud.java: HandlePictureStorage() gets the byte array of the photo
4. CameraCloud.java: sendClientNewpic() compresses the photo 
5. CameraCloud.java: serverRequest() sets up an Http connection to the cloud server 
6.      The time is taken down for latency calculations
7.      An HttpPost with the photo data (and others, see diagram below) is executed via 3G or 4G.
8.      If there are any exceptions that would cause the HttpPost to not execute, the `takeException` counter is incremented.
9.      In the case that there is a response, the `takeGood` counter is only incremented if there is not a null response, errorneous return status. Otherwise, `takeBad` is incremented.
10.      Latency is calculated
11. CameraCloud.java: sendClientNewpic() enables UI button presses again

        Phone  ------ 3G or 4G ------> Cloud
                HttpPost data:
                - CLIENT_UPLOAD_PHOTO
                - Region number 
                - Photo byte array
    

Scenario 2. Cloud Get Region 5
---------------------------
1. User presses the "Get 5 Photo" button on phone.
2. CameraCloud.java: get_button_listener OnClickListener() 
        registers the button press and knows the destination region is 5 and disable all other button presses with ProgressDialog until response is received in step XX
4. CameraCloud.java: serverRequest() sets up an Http connection to the cloud server 
5.      The time is taken down for latency calculations
6.      An HttpPost with the photo data (and others, see diagram below) is executed via 3G or 4G.
8.      If there are any exceptions that would cause the HttpPost to not execute, the `getException` counter is incremented.
9.      In the case that there is a response, the `getGood` counter is only incremented if an actual photo is successfully received. So the "bad" counter is incremented in all other responses, such as null response, errorneous return statuses, or if a region does not have a photo.
10.      Latency is calculated
11. CameraCloud.java: get_button_listener enables UI button presses again

        Phone  ------ 3G or 4G ------> Cloud
                HttpPost data:
                - CLIENT_DOWNLOAD_PHOTO
                - Region number 
                

-------

DIPLOMA
bad = (isSuccess == false): some or all of this is due to DIPLOMA Level time out
timedOut = timed out after 6 seconds. If request comes back after 6 seconds, it is counted into getGood or getBad

Code Set Up
==============================

- The IP addresses of the phones should only differ in their last number. Their identical first 3 numbers should also be set for the first 3 numbers of Globals.BROADCAST_ADDRESS. During my project. the phones had IP addresses of 192.168.5.[number], so the Globals.BROADCAST_ADDRESS was set to "192.168.5.255" 
    https://github.com/haoqili/Android_DIPLOMA_CAMERA/commit/c96695f6d4246bedc1395cffac9e184e807ba53e#L1R16

- 

Experiment Set Up
==============================
- Run DIPLOMA and Cloud servers and make sure their IP addresses are set on Globals.CSM_SERVER_NAME and Globals.CLOUD_SERVER_NAME respectively.
- Start Barnacle on DIPLOMA phones

Pre Trial Issues
==============================

Trials
==============================

Prior to the first experiment. We conducted two mini experiments to see the app was working and to test the wifi range of the phones.

Mini expe
conducted a 3-phone test to get the Wifi range of the phones. In this test, the phones were facing each other all the time and there were no obstructions in the path of transmission. We would later find out that the range from this test would be too large for multi-user, randomized direction, randomized obstruction data-collecting experiments.

We performed a total of 6 data-collection experiments in a span of almost 2 months. Throuh time, the apps had fewer bugs and more measures to insure successes. However, it was impossible to fix the most critical issue -- the Wifi range and consistency of the phones. The interference of 20 phones carried by 10 people moving simultaneously and randomly made collecting meaninful data imfeasible with the current Wifi abilities of the phones. In the final 2 experiments, we resorted to a controlled indoors experiment with minimal Wifi interferance and obtained more expected results.

Experiment 1
------------
Location: 77 Massachusetts Avenue
Date: March 15
Phones: 20 Nexus S
People: 10 People, 1 Cloud, 1 DIPLOMA/person
Regions: 6
Setup: Linear
Width: 

In this experiment was filled with problems with the apps due to insufficient and inadequate testing beforehand. There were frequent crashes due to the Camera Surface interface running out of memory, and the users double pressing the buttons. The regions were XX meters wide each, which caused great trouble in phones communicating with each other even in the same region. A smaller bug forced the users to walk to region 0 whenever the apps crashed. The region assignment based on GPS was observed to be robust.

- Fixed region 0 problem
- Decreased cloud heartbeat
- Added "take photo" ack
- Added latency measurements
- Fixed double click problem by using ProgressDialog to freeze the UI as well as a boolean to flag whenever a button is clicked.
- Improved UI by providing toast messages to show errors to users, filling up entire Galaxy Note screen,
- Fixed leader hand-off bug by adding a new state "HANDING_OFF"
- Fixed the bug where leaders were not sending heartbeats to cloud
- Automatic detection for net name
- 

Experiment 2
------------
Location: 436 Massachusetts Avenue
Date: April 6
Phones: 20 Nexus S, 20 Galaxy Notes
People: 10 People, 1 Cloud, 1 DIPLOMA/person
Regions: 6
Setup: Linear
Width: 

People complained about phones being out-of-range and waiting for a long time to JOIN a region. Initially the cloud server was unresponsive, but after restarting, it was better. We had to restart the server multiple times.  The experimental successes were much higher when there were stationary region leaders. 
TODO: XX Provide actual results

- Added acks for first and final legs
- Moved code order on leaders to more accurately record successes and fails
- UI: show number of successes
- Fix HANDING_OFF bugs so now HANDING_OFF can
    - send heartbeats
    - let non-leaders of region elect new leader
    - release leadership to cloud!  <-- cause of hanging in JOINING because old leaders never cloud release their leadership to the cloud and allow new leaders to take over. So the only leader transitions occured due to cloud timeouts, which was XX about 2 minutes.
- Added editable width and hysteresis
- Improve performance: if JOINING hears a heartbeat from the region, it tries to become become NONLEADER right away

Experiment 3
------------
Location: 77 Massachusetts Avenue
Date: April 25
Phones: 20 Nexus S, 20 Galaxy Notes
People: 10 People, 1 Cloud, 1 DIPLOMA/person
Regions: 6 or 4

In order to have a stable server, we used a ethernet-connected laptop and observed no server crashes. The range was still bad at 20 meters. However at 10 meters it would be too small for the DIPLOMA hops to make sense since the GPS locations varied a lot. *TODO Expand/explain*


Experiment 4
-----------
Location: Inside Stata, in the lounge closest to the Vassar/Main St intersection    
Date: April 30
Phones: 20 Nexus S, 20 Galaxy Notes
People: 10 People, 1 Cloud, 1 DIPLOMA/person
Regions: 6 or 4

No GPS, hystersis, DIPLOMA, or multihop. Users simulated regions by pressing buttons according to area on floor. Observed 3G better than 4G. Also during the 3G trial, people started to hold their phones more vertically. 
Observed a bug where sometimes an entire region cannot get/take pictures successfully.

- Fixed the bug where the entire region cannot get/take pictures. This was due to an error in the ack counter. The reply of a request counter should be derived from the request counter. ** Should I explain why I made this mistake ?? TODO ASK: XXX **

Experiment 5
-----------
Location: Inside Stata, in the lounge closest to the Vassar/Main St intersection    
Date: May 6
Phones: 20 Nexus S, 20 Galaxy Notes
People: 2, controlled experiment
Regions: 6 o

Observed that latency was somewhat strange.

- Added latency UI

Experiment 6
-----------
Location: Inside Stata, in the lounge closest to the Vassar/Main St intersection    
Date: May 12
Phones: 20 Nexus S, 20 Galaxy Notes
People: 2, controlled experiment
Regions: 6 

Figured out why latency was strange. Expected results, finally
Though for one, we were 3% short of 100%

======================================
- link to github commits of the bugs
- link to raw data
- description of methodology
- emails ...
- conclusions
    - bug fixes
    - low level system issue
    - how to reproduce the issues/repeatable

- how things got to where they are
