UI/Functionality

Two Apps

* Diploma version
* Cloud version

Experimental data
* Expected results
* Experimental data



The experiments ran two different apps that looked and functioned identically from the user’s perspective, but have completely different mechanisms of operation in the background.  First, we will describe the functionality and the UI of the apps, then we will describe what goes on in the background for each of the apps and finally, we will compare and contrast the expected and experimental data of the two apps. 

UI /Functionality (Same for Two Apps)
==================
The goal of the experiment is for users to share photos among themselves using their smart phones.  The smart phones can take new photos and request the latest photos taken by other phones. But unlike traditional photo sharing apps where each phone functions individually, our experiment assigns the phones into different regions based on their GPS locations and a region’s phones collectively save their photos.  This implies: a) a new photo is saved on its phone’s region, not the phone itself and b) a phone can only request the newest photo of a region, not of another individual phone. 
 
In our experiment, six square consecutive regions (0 to 5) about 30 meters wide were created along a stretch of busy road.  Ideally, there is no limit to the number of regions as long as a phone can only belong to one region at any time. The size of the region should be as big as possible, but still small enough that phones from any points in the region are within WIFI ranges from each other. The users walked around and pressed buttons on the apps to take new photos or request photos from different regions. With two apps on two different phones, the users synchronized the button presses so that sequences of events for the two app types are similar.


The Two Apps
============
The two different apps are: DiplomaCamera and CloudCamera. Even though their UI are identical, DiplomaCamera handles the pictures using DIPLOMA while CloudCamera is the control of the experiment, handling all requests with the cloud.

DiplomaCamera
--------------------

The DIPLOMA design introduces a leader in each of the regions.  The leaders take care of all the requests coming from all phones (clients) in the region.  When a phone takes a new photo, it broadcasts the photo data to its leader, where the photo is saved. When a phone requests a photo from Region X, this request is broadcasted to its leader, which in turn uses DIPLOMA to relay the request and receive Region X’s photo data from X’s leader.

The code is divided into three big components: 

1.  StatusActivity.java for UI and client processing
2.  UserApp.java for leader and remote leader functions
3.  The DIPLOMA java files: Mux.java, VCoreDaemon.java, DSMLayer.java are unchanged.

StatusActivity.java contains listeners for the button presses that send requests to its region leader and a handler that processes replies from the region leader. Each phone has a unique id based on its IP address that can help a region’s leader distinguish the non-leader phones in its region.  

Pressing the button that takes a picture triggers that button’s listener to retrieve the photo information from the Camera SurfaceView. The photo data is then put into a packet along with the phone’s ID, the phone’s region number, and type of request (Upload_photo). This packet is serialized inside StatusActivity.java into a UDP broadcast that reaches the leader of the region.

Similarly, pressing a button that requests the newest photo from region X triggers the request buttons’ listener to get information on the target region number that the user is requesting. A UDP packet consisting of the phone’s ID, the phone’s region number, the target region number, and the type of request (Download_photo). Again, StatusActivity.java broadcasts this packet to the leader of the region.

Let Original Leader (OL) be the leader of the phone that made the request. 

OL’s UserApp.java:handleClientRequest processes the UDP packet by the type of request. In both cases, OL sends a DIPLOMA DSM atom request, along with the additional information from the UDP packet, to the Remote Leader (RL). In the Upload_photo case, RL is the same as the OL, since new photos are processed locally. In the Download_photo case, RL is the leader of the target region. RL’s UserApp.java:handleDSMRequest processes the DSM atom request from the OL. For Upload_photo, the photo info is saved in RL’s DIPLOMA memory as the first element of an ArrayList. (For the experiment, we only save one photo at a time. But theoretically, there is no limit to the number of photos that can be saved.) The reverse happens for Download_photo, where RL retrieves the newest photo from its DIPLOMA memory.  In both cases, RL sends a reply back to the OL, arriving at OL’s UserApp.java:handleDSMReply which sends a UDP packet containing DIPLOMA latency and a success boolean, and also the photo data in the case of download_photo, back to the original non-leader.

Finally back to the original phone, its StatusActivity.java handler gets the UDP reply from OL and logs the replies. In the case of Download_photo, the remote region’s newest photo is displayed.

* I don’t think the UI of leader showing new photos from nonleaders is important to write about

Leader transitions (it’s covered by other parts of the paper, right?)
--------------------------
* leader go out of region TODO: add handing-off state?
* leader is dead (cloud wait 100 secs to allow new leader)
* leader heartbeat to the cloud and to non-leaders

 Other things
---------------
To avoid confusion and inconsistency of the region numbers, phone buttons only work if the phone is in a LEADER or NONLEADER state.  To avoid double-sending a request (and possibly crash the camera surface view), a ProgressDialog is shown until the client has received a leader reply or until a timeout. 


CloudCamera
------------------

In CloudCamera, every request is sent to the cloud server. The cloud server keeps a dictionary linking each region to its newest photo.  Codewise, CloudCamera only has one file: CameraCloud.java that is analogous to DiplomaCamera’s StatusActivity.java, but instead of sending UDP packets, CloudCamera sends HTTP post requests. 

Experimental data
================
We expect the CloudCamera with 3G connections to have longer latencies based on the premise that short-range adhoc WIFI is faster than 3G.

* Experimental data


