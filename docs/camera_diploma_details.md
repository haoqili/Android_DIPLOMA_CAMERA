Order:
X1. Camera_DIPLOMA take
X2. Camera_CLOUD
3. Trials + heartbeat/talking section of DIPLOMA overview
4. screen shot DDSM / UI writing
5. DIPLOMA overview (introduces region leader relaying/hopping, consistent shared memory among region, heartbeats/talking to cloud detailed enough for the trials section, or write it along with the trials section)

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

################
################

Code Set Up
==============================

- The IP addresses of the phones should only differ in their last number. Their identical first 3 numbers should also be set for the first 3 numbers of Globals.BROADCAST_ADDRESS. During my project. the phones had IP addresses of 192.168.5.[number], so the Globals.BROADCAST_ADDRESS was set to "192.168.5.255" 
    https://github.com/haoqili/Android_DIPLOMA_CAMERA/commit/c96695f6d4246bedc1395cffac9e184e807ba53e#L1R16

- 

Experiment Set Up
==============================
- Run DIPLOMA and Cloud servers and make sure their IP addresses are set on Globals.CSM_SERVER_NAME and Globals.CLOUD_SERVER_NAME respectively.
- Start Barnacle on DIPLOMA phones
- The first time Barnacle starts it'll ask for WPA Supplicant. Keep choosing "Yes" and it will create the WPA supplicant.
- On March 10th, we observed Barnacle printing an error saying : "Cannot put eth0 in ad-hoc mode" on the AT&T phone but it still continues to work fine. We do not observe this error later on.
1. SMCloud server running ?
2. DIPLOMA server running ?
3. Ensure Items 1 and 2 above do not run on the same port on the same machine.
4. The code on the phone must point to the right server in both cases
1 and 2 above.
5. Is the code  resilient to phone being switched off at random. ?
6. UI does not freeze at any time except for when processing is going on.
7. Clear log files from sdcard of all phones.
8. Make sure logs are flushed on to the sdcard immediately so that we
don't end up with empty logs at the end of an experiment.

- If the request is taking too long, you might see: "xxx is not responding. Would you like to close it? 'Wait' 'Okay'". If people press 'Okay'. the app crashes. If people press 'Wait' the app continues normally. Don't press anything else


Notes in chronological order
==============================
- Whenever after the phone connects to USB, you have to turn on the development mode again.

- Socket-already-in-use problem when loading new code is fixed by always killing (using back arrow) the app before loading new code. (The "home" button only pauses the app, not destroying it.)
    Jason: The socket in use is probably due to the NetworkThread not exiting cleanly or at all when you load new code onto it. When you load new code directly from your laptop without first closing the running app by hitting the back button, ADB kills the StatusActivity without letting it close the NetworkThread first.

- Before I used my own Camera Surface View, I used the built-in camera ACTION_IMAGE_CAPTURE intent to get the picture: https://github.com/haoqili/Android_DIPLOMA_CAMERA/blob/e61381eda31d567bb31c69c21f54d3adb9c9f044/src/edu/mit/csail/diplomamatrix/StatusActivity.java#L382. Initially there was a problem that the simple way of retrieving images only provided a *tiny thumbnail of the image, not the full image*: see https://github.com/haoqili/Android_DIPLOMA_CAMERA/blob/a1398a4723c0e6710a43dddb8681081c910875ff/src/edu/mit/csail/diplomamatrix/StatusActivity.java#L364
    The problem is also described here: http://stackoverflow.com/questions/1910608/android-action-image-capture-intent
More specifically, the onActivityResult() of intent image capture can only provide a low-resolution thumbnail because there probably isn't enough storage space all the time to save all the image data. So that's why http://developer.android.com/reference/android/provider/MediaStore.html#ACTION_IMAGE_CAPTURE says that EXTRA_OUTPUT should be used to save the image.
To fix this problem (ultimately I changed into Camera Surface View though and don't use the camera intent at all), I save the new photo on the SD card and onACtivityResult() uses _getAndResizeBitmap() to retreive the photo and resize it. https://github.com/haoqili/Android_DIPLOMA_CAMERA/commit/e61381eda31d567bb31c69c21f54d3adb9c9f044#L4R384

- GPS is accurate within 3-5 meters. Each phone has a different consistent offset, e.g. a phone could always be 2 meters more south than the other phones.
- Sometimes the GPS will have a glitch and jump suddenly to a ridiculous location, but this happens so rarely and we don't need to do anything about it

- On March 10th, Anirudh and HaoQi went to check out the stretch of Mass Ave outside of McDonalds and Cafe Luna to get measurements for the 1st experiment. Since that stretch of Mass ave was very straight and we were not yet aware of the limitations of the Wifi range, we thought we could just use the x coordinates of the phones (already rotated so that the axes correspond to east-west and north-south directions) with region 1 starting at the right (south eastern) most region of the road and the regions increase westwards (northwestward of the road).  The regions shapes are not rectangles, but parralelograms (which we'll change to rectangles later so that region width can increase).

    GPS Results from our micro-measurements (NOT USEFUL, but for a record. Later on we discovered there are too many Wifi hotspots causing too much interferance in this stretch of the road)
        A picture from the measurement, you can also see the UI at that time: https://github.com/haoqili/Android_DIPLOMA_CAMERA/blob/6c7252d3d9f66a57e3ed84d0135ab021846a1e10/docs/2012_03_10_diploma.JPG
        Google Maps puts the distance between Bank Of America (BOA) and Desi Dhabha on Mass Ave at 0.3 miles which is 540 m
        From Google Maps
            42.363404,-71.100214 Desi Dhaba
            42.366067,-71.104761 BOA 
            Assuming coordinates are planar : distance is 528 m, 10^(-5) degrees is 1 m.
        From phones :
            BOA
                Nexus S :
                42.366300,-71.104936
                Galaxy Note :
                42.365944,-71.104888
                Not instantaneous, they were not taken at the exact same location or time, we moved in about a 5 m radius :
                GPS error between phones ~ sqrt(36^2+5^2)  ~ 37 m  which is kind of ok given that we may have moved quite a bit.
            Desi Dhabha :
                Nexus S:
                -71.100005,42.363466
                Galaxy Note :
                -71.100005,42.363492
                exact same location
                Difference is ~ 3 m That's really accurate between two phones
        Summary of Distances.
            Distance acc to GMaps: 528 m
            Distance acc to Nexus S : 568 m
            Distance acc to Galaxy Note : 546 m

- (very minor ui) `photoResultNull.setVisibility(View.VISIBLE);` of https://github.com/haoqili/Android_DIPLOMA_CAMERA/commit/8de606e548b4854807ea91a4822b7638a250843c didn't work so I stopped using it.

- **Important!** https://github.com/haoqili/Android_DIPLOMA_CAMERA/commit/333ea188283ab9b67b03c5343ecdc35c01e9fda8#L8R208
    This commit is when I switched from using camera intent to retrieve the picture and saving the pic on the SD card to using the CameraSurfaceView class http://code.google.com/p/openmobster/wiki/CameraTutorial.
    The reason for this change was because the *intent/sd card solution only works on Nexus S phones, not Galaxy Notes* phones. In Galaxy Notes, the Mux is killed and restarted before and after the camera intent (because StatusActivity is paused), causing a "Cannot open socketAddress already in use" error: https://github.com/haoqili/Android_DIPLOMA_CAMERA/blob/8de606e548b4854807ea91a4822b7638a250843c/logcats/galaxy_note_camera_crash.txt#L470
    CameraSurfaceView fixes this problem because StatusActivity never has to be paused when taking a photo. Since it works on both types of phones, we used this solution for both.

- ! Much better GPS to region logic, with help from lfei. https://github.com/haoqili/Android_DIPLOMA_CAMERA/commit/460a917a4a73594ddd3b48c4109a36935c23c3d1 This takes into account that longitude distances varies greatly from the poles (0) to equator (longest). So the regions can be assigned more accurately. The regions are square. The road is defined by 2 points. (So the road must be straight.)  The final implementation of this in the latest code can be tweaked more easily.


Experiments
==============================

We conducted two pre-experiments to see the app was working and to test the wifi range of the phones.

Pre-experiment 1: 3-phone test to check DIPLOMA relay and Wifi range
    There were 3 people, each holding a phone. One phone stood at a corner of a building while the other phones each stood about 20 meters along a different wall. The middle phone is in range of the other two phones, but the other two phones are not in range with each other, in Wifi and sight. The phones were held vertically, the outer phones faced the middle phone. There were no obstructions in the path of transmission. We would later find out that the range from this test would be too optimistic for multi-user experiments where users moved around and obstructed each other all the time.
    Let the outer phones be A and C, and the inner phone be B. Phone A would take a picture and let phone B know (by using gestures) that B can tell C to request A's picture. If A successfully gets B's photo, then DIPLOMA multi-hop works because the only way for A to succeed is for B to help pass along the picture from C to A in DIPLOMA.  
    When we stood 20 meters apart, the "get" worked about 2 out of 4 times.

Pre-experiment 2: Testing that phones can hear each other across Mass Ave.
    Even though all of the outdoor experiments were conducted on one side of the rode, we tested that Wifi signal could potentially work between phones across the road.  Two of us stood at opposite sidewalks taking and getting each other's picture. We did not observe anything abnormal in the 5 mintue experiment. 


i   We performed a total of 6 data-collection experiments in a span of almost 2 months. Throuh time, the apps had fewer bugs and more measures to insure successes. However, it was impossible to fix the most critical issue -- the Wifi range and consistency of the phones. The interference of 20 phones carried by 10 people moving simultaneously and randomly made collecting meaninful data imfeasible with the current Wifi abilities of the phones. In the final 2 experiments, we resorted to a controlled indoors experiment with minimal Wifi interferance and obtained more expected results.

Experiment 1
------------
Location: 77 Massachusetts Avenue
Date: March 15
Weather: Drizzling and cold
Phones: 20 Nexus S
People: 10 People, 1 Cloud, 1 DIPLOMA/person
Regions: 6
Setup: Linear
Width: 
Files: https://github.com/haoqili/Android_DIPLOMA_CAMERA/tree/master/camera_diploma_exp1_data, 
       https://github.com/haoqili/Android_DIPLOMA_CAMERA/blob/master/camera_diploma_exp1_data/diploma_notes.md
       https://github.com/haoqili/Android_DIPLOMA_CAMERA/blob/master/camera_diploma_exp1_data/cloud_notes.md

No usable data from this experiment because this this experiment was filled with problems due to insufficient and inadequate stress testing beforehand. There were frequent crashes due to the Camera Surface interface running out of memory, and the users double pressing the buttons. The regions were XX meters wide each, which caused great trouble in phones communicating with each other even in the same region. A smaller bug forced the users to walk to region 0 whenever the apps crashed. The region assignment based on GPS was observed to be robust.

The git commits between this experiment and the next fixed the following:
- Added leader to cloud heartbeat, i.e. fixed the bug where leaders were not sending heartbeats to the cloud
- Fixed region 0 problem, so that after phone crash, the user can restart at any region, not just region 0.
- Added "take photo" ack, i.e. the 4th leg for take photos
- Added latency measurements
- Improved UI by providing toast messages to show errors to users
- Flush the log evertime there is a new write, instead of when the app closes, so that we have logs even when the app crashed. https://github.com/haoqili/Android_DIPLOMA_CAMERA/commit/6c95044a8662de19f2d1b6b8d1043e033da5741c#L30R262
- Automatic detection for net name, 

- Fixed double click problem by using ProgressDialog to freeze the UI as well as a boolean to flag whenever a button is clicked.
  The Double Click Problem: 
    Before we tried using the boolean flag, we tried to make the camera surface view better by closing the camera and force closing the app 
    https://github.com/haoqili/Android_DIPLOMA_CAMERA/commit/5827245dd39eddd7d6097caa2ff10b5949a73448#L7R35
    https://github.com/haoqili/Android_DIPLOMA_CAMERA/commit/5827245dd39eddd7d6097caa2ff10b5949a73448#L7R72
    https://github.com/haoqili/Android_DIPLOMA_CAMERA/commit/017939513daeb33cd9682c717752c89957acc18e#L29R483
    Force closing added to be extra sure nothing bad happens, because we learned that the camera crash problem is caused by camera not being killed even though DIPLOMA has forced stopped and destroyed.
    http://stackoverflow.com/questions/2092951/how-to-close-android-application/5036668#5036668
    https://github.com/haoqili/Android_DIPLOMA_CAMERA/commit/6c95044a8662de19f2d1b6b8d1043e033da5741c#L30R524

    We didn't stress-test these changes independently, but instead tested with the boolean flag that disables all other button presses when a press is made: 
    https://github.com/haoqili/Android_DIPLOMA_CAMERA/commit/5827245dd39eddd7d6097caa2ff10b5949a73448#L11R424 
    https://github.com/haoqili/Android_DIPLOMA_CAMERA/commit/017939513daeb33cd9682c717752c89957acc18e#L29R345
    
    No camera crashes were discovered after these changes

- Progress Dialogs do not stop the popup of "xxx is not responding. Would you like to close it? 'Wait' 'Okay'". If people press 'Okay'. the app crashes. If people press 'Wait' the app continues normally.
  For the Cloud App GETs: ProgressDialog does not show the rotating sphere/darkened screen, but instead just freezes the UI. No harmful effects were observed.
    IRC says: "that dialog shows because your app is not responsive, that is, it won't respond to *system* signals as well"
    It seems true, because the popup only shows if the ProgressDialog's wheel has stopped spinning for a while. This happens if the connection is ultra crappy (indoors).
    Despite being unresponsive for a long time, I haven't seen crashes unless I press 'Okay'.

- Fixed leader hand-off bug by adding a new state "HANDING_OFF"
  Handing_OFF
    We added a HANDING_OFF state, when a leader of one region hands off its leadership.  Here are the diffs we made to VCoreDaemon 
    https://github.com/haoqili/Android_DIPLOMA_CAMERA/commit/eeda40d5f628aa609a5111e02ea1995666b9e451#diff-3

    Here is suvinay's crash logs:
    https://github.com/haoqili/Android_DIPLOMA_CAMERA/blob/5a26e47024fa7630fa3bc86b2107980ddc24482d/logcats/0330_1251/whitephone/suvinay_crash.txt

    The reason we did this was because we (Suvinay/Owen) discovered a consistent problem where before the leader handoff is done, the old leader thinks it's the new region's leader before it even joins the new region. E.g. if the leader of region 3 moves to region 2, this is the sequence of state and region the phone shows:

    Leader (3,0) --> Leader (2, 0) --> JOINING (2, 0) --> Leader (2, 0)

    The second stage (The first "Leader (2,0)") is very short, but if a "Reg # Get" is pressed during this time, the phone freezes because the phone is actually NOT a leader of region 2 yet.

    So we edit it so that it's like

    Leader (3,0) --> HANDING_OFF --> JOINING (2, 0) --> Leader (2, 0)

    Now, we make sure that users can press buttons ONLY if the state of the phone is a Leader or Nonleader:
    https://github.com/haoqili/Android_DIPLOMA_CAMERA/commit/eeda40d5f628aa609a5111e02ea1995666b9e451#L1R350

    HANDING_OFF introduced bug fixes:
    - Make HANDING_OFF able to receive leader nominates and send leader confirmation ack, i.e. making electing new leader possibles
        https://github.com/haoqili/Android_DIPLOMA_CAMERA/commit/4bdab778fcbc0cd00187eb4bf679946abaa6bec6#L0R333
    - Change the HANDING_OFF state transition to the correct region
        https://github.com/haoqili/Android_DIPLOMA_CAMERA/commit/4bdab778fcbc0cd00187eb4bf679946abaa6bec6#L0R695

- Decreased cloud heartbeat, because the old period was too long, which was okay with caching (Jason's old app), but this app doesn't have caching
    https://github.com/haoqili/Android_DIPLOMA_CAMERA/commit/6b3533e98b47b981ed0ba0594f10d4e119caaa92#L4R54

- Improve UI of Galaxy Note by filling up the screen space from about 60% to 100%
    https://github.com/haoqili/Android_DIPLOMA_CAMERA/commit/4ca30c50f7b504a7ac6c5b6cc7cec1eced896860#L0R5

- *N.B.* Galaxy Notes and Nexus S phones require different net names. Galaxy Note needs "eth0" while Nexus S needs "wlan0". Before this commit, I would change the string manually, but this commit allows for automatic detection of the type of phone:
    https://github.com/haoqili/Android_DIPLOMA_CAMERA/commit/4ca30c50f7b504a7ac6c5b6cc7cec1eced896860#L2R486

- OutOfMemoryError
    Another reason that could have contributed to the crash, on the Nexus S phones, was taking too many pictures in a row causing the VM heap to run out of memory at BitmapFactory.decodeByteArray(): 
    https://github.com/haoqili/Android_DIPLOMA_CAMERA/commit/e3e9e5ab069b4f57e6214fd8e6e0300f5d5305cc#L8R325
    http://stackoverflow.com/questions/477572/android-strange-out-of-memory-issue-while-loading-an-image-to-a-bitmap-object
    http://stackoverflow.com/questions/6402858/android-outofmemoryerror-bitmap-size-exceeds-vm-budget
    We observed the memory inside Eclipse's DDMS - Heap profile. 
        The following instructions are from memory, so might not be all correct, consult Google
        Open Eclipse's DDMS
        Left hand side, click on Devices, then click on the icon that dumps HPROF file
        Right hand side click on the Heap tab, and click "Cause GC"
    To reproduce the problem, quickly press back-to-back TAKEs on a Nexus S phone (leader or nonleader)  and there could be a crash on the 3~6th TAKE, after all previous TAKEs are successful
    We observed the free memory fluctuation real time. But we didn't notice a consistent drop in free memory, or consistent decrease of free memory for new TAKE pictures. 
    At first trying catching the exception and putting in  bitmap.recycle() didn't work. 
    Here are the two workarounds that did work
        1. Set the inSampleSize for jpegs to be a big number, so more pixels are skipped and thus it would cause a blurrier and smaller picture:
            https://github.com/haoqili/Android_DIPLOMA_CAMERA/commit/dbc267a51799ed40e40751122425e9543e0bd5e8#L1R749
        2. Add garbage collect in many places associated with taking pictures and the memory-intensive resizing the pictures
            https://github.com/haoqili/Android_DIPLOMA_CAMERA/commit/dbc267a51799ed40e40751122425e9543e0bd5e8#L1R144
    After these two workarounds were coded, we tested the phone by continuously pressing the TAKEs over 100 times, multiple times, and did not observe any crashes.


- UI improvement: Added counts for success and failures
    https://github.com/haoqili/Android_DIPLOMA_CAMERA/commit/92ccb731dbb4d140001c831602aa7ac570766bd0

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

Closed Questions
=============

Waiting Period
-----------
Q: 
Hi Jason, In VCoreDaemon, what's the purpose of rejoining a region every 10 seconds (stateRequestedTimeoutPeriod = 10000ms) with "Runnable stateRequestedTimeoutR" when we already have "Runnable heartbeatR" to make sure that the leader is alive every 3*heartbeatPeriod time?  

A:
That's in case you're in a "WAITING" state, which is a rest period of
10 seconds or so to make sure we're not just constantly doing leader
request and cloud take leadership cycles too fast.

We can make it shorter though, perhaps.

e.g.

sent leader_req
sent leader_req
sent leader_req
sent leader_req
leader_req timeout
trying to take leadership in cloud
take leadership failed
waiting 10 seconds to cool off / for conditions outside of our control
to change (e.g. mobile data available, out of range, etc.)

v.s.
sent leader_req
sent leader_req
sent leader_req
sent leader_req
leader_req timeout
trying to take leadership in cloud
take leadership failed
redo all the steps above immediately, even though conditions might not
have changed much

Q:
What are the circumstances that would cause a phone to be in a WAITING state?

A:
Leader not reachable and unable to take leadership from cloud. Both of those have to be true.

--

Open Concerns
=============
- ad hoc wifi continues running on the AT&T phones even after we turn off the Barnacle app (assuming we did turn it on once at least to get wifi working in the first place.). ie turning off the barnacle app has no effect on ad hoc wifi on the AT&T phone. Do you know why this is happening ?

- Extremely unlikely race condition (not fixed, we didn't encounter this, though this could happen)
    Chronological order:
    1. The phone makes Request 1 and ProgressDialog is activated to freeze the UI
    2. Request 1 times out so ProgressDialog is disabled and the UI is unfrozen
    3. Right after, the phone makes Request 2 a TAKE, and the ProgressDialog is activated again so UI is frozen again for this request
    4. Request 1's reply comes back and disables any ProgressDialog, which in this case is actually the ProgressDialog to make sure Request 2's TAKE's camera does not have the double click (quickly activating the camera twice crashes the camera, see "Double Click Problem" above) problem.
    5. *Immediately* after the ProgressDialog is disabled, the user clicks on another TAKE, and *could encounter the Doble Click Problem*
    Fixes:
        1. Save latest request id in StatusActivity (in a queue or variable) and only if the request id from the received packet matches the latest request id can the ProgressDialog be dismissed.
        or 2. Independent timeout for camera

- OutOfMemoryError (Search above)
