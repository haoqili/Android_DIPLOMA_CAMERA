General
========
Logs are named by time stamp. 

The experiment did not start until you see the line:
`location CHANGED TO NEW region = (1,0) from region = (-1,-1)`

If the app crashed, you have to find that line again to determine when to start reading data

You can request picture from any region, even if you are not in a valid region.

`Inside mux Packet.CLIENT_REQUEST` can be seen at any time (before getting into a valid region) by anyone (nonleader/leader), but it's only intended for the leader of the nonclient originator and ignored by anyone else. I.e., don't worry if you see this line randomly.

TO ASK
======

* If a phone does not get leader replies, it sends a request to the cloud to become the leader. But what if the request to the leader is not successful?  Does that mean that this phone is cut-off from the rest of the DIPLOMA phones?  I.e. it can't take pictures (since there is no leader to save the picture) or request pictures (since there is no leader to relay its request). 
If so, should we just disable the phone from taking and requesting pictures?

* Is it normal to have way more "LEADER_REQUEST timed out"s than "heard LEADER_REPLY"?  Because this is what I observe.  If it's not normal, then I guess we should decrease the region width because perhaps leader and new phones that arrived in the region can't hear each other?

more 

    1331844666510: sending LEADER_REQUEST
    1331844666512: Sending UDP payload: 426
    1331844666515: LEADER_REQUEST timed out
    (1331844667080: cloud rejected leadership request or request failed, wait to retry)

than 

    heard LEADER_REPLY from 5007

* What if a leader is going out of the region and hands the leadership to the cloud, but cannot reach the cloud even after retries? "cloud rejected leadership request or request failed, wait to retry"  I guess the other nodes would detect that there is no heartbeat from the old leader and elect a leader among themselves?  If so, what if the cloud is unreachable again?  (see 7's logs)


TO CHANGE
=======

* Can start at any region, i.e. doesn't have to go back to region 0 everytime app crashes
* Inside UserApp `Inside CLIENT_NEW_PHOTO` show id of photo taker and 
* Show if picture sent to leader failed (3 retries) or succeeded, i.e. ack to that photo taker!
* record latency time!
* log when requesting photo is displayed
* Either make region 0 dormant or make "get photo from 0" button
* Increase boundary width (if necessary)

UI Mistakes
=========
* Disallow pictures to be taken when not in valid regions (1-6)
* Show a (sending to server ....) after photo is taken followed by a (success) or (failed)
* make set regions harder or disable it
* Make the 2 apps more distinguishable ~ different background colors? So people can know which app is which and do not accident switch apps in the middle of the experiment

Logcats
======

Location chunk regex filter: `^133.*tionChanged.*\n^133.*INSIDE DET.*\n^133.*mBearing.*\n^133.*GPS x\/long.*\n^133.* unrotated.*\n^133.*rotated.*\n^133.*PINPOINTS to region = `

b
====

2647088
----

1331843613923: now fully up as LEADER (id=5001) of (0,0)

1331843802993: Picture successfully taken, ORIG BYTE LENGTH = 2112857
1331843804226: Inside mux Packet.CLIENT_REQUEST
1331843804226: Inside CLIENT_NEW_PHOTO!!
1331843804226: Sending PROC_REQUEST 10:0 (0,0)->(0,0)
1331843804227: Dispatching Atom PROC_REQUEST 10:0 (0,0)->(0,0)
1331843804289: Show photo from server_show_newphoto
1331843804248: Inside UPLOAD_PHOTO!!
1331843804256: Upload Photo succeeded

1331843815745: Inside CLIENT_DOWNLOAD_PHOTO, figure out where to forward packet
1331843815753: Sending to region: 4
1331843815763: Sending PROC_REQUEST 11:0 (0,0)->(4,0)
1331843816732: Retrying PROC_REQUEST 11:0 (0,0)->(4,0)
all retries failed and 
1331843817958: Request timed out, send failure PROC_REPLY 11:0 (4,0)->(0,0)

1331843834706: Inside CLIENT_DOWNLOAD_PHOTO, figure out where to forward    packet
1331843834707: Sending to region: 5
1331843834713: Sending UDP payload: 956
1331843834714: nonce 18 heard from src region (0,0)
1331843834792: Received UDP payload: 956
1331843834810: GOT CSM_Msg
1331843835411: Retrying PROC_REQUEST 11:0 (0,0)->(5,0)
1331843837273: Request timed out, send failure PROC_REPLY 11:0 (5,0)->(0,0)

1331843840808: Inside CLIENT_DOWNLOAD_PHOTO, figure out where to forward    packet
1331843840809: Sending to region: 4

1331843844586: now NONLEADER (id=5001) following LEADER (id=5003) in (1,0)

1331843847998: now NONLEADER (id=5001) following LEADER (id=5003) in (1,0)

3 more `Packet.CLIENT_REQUEST` after when the phone became a nonleader (these are client requests not intended for this phone), this is normal behavior since this phone correctly ignores them, but it crashes shortly after the last request, possibly unrelated though.

1331843849529: Inside mux Packet.CLIENT_REQUEST
1331843856061: Inside mux Packet.CLIENT_REQUEST

As a leader of the region, it processed photos it took itself 2 times, and photos its nonleaders took 2 times:

    1331843817075: Picture successfully taken, ORIG BYTE LENGTH = 1966064
    1331843818287: Our new height x width: 240 x 320
    1331843818288: Show photo from handle my camera take
    1331843818288: client making photo packet to send to leader
    1331843818312: BYTE SIZE AFTER COMPRESSION: 4289
    1331843818313: Inside mux Packet.CLIENT_REQUEST
    1331843818313: Inside CLIENT_NEW_PHOTO!!
    1331843818314: Sending PROC_REQUEST 10:1 (0,0)->(0,0)
    1331843818314: Dispatching Atom PROC_REQUEST 10:1 (0,0)->(0,0)
    1331843818314: I got a CSM packet at a leader

    1331843818378: Show photo from server_show_newphoto
    1331843818317: Sending UDP payload: 5101
    1331843818318: removed replies before id 1 from sentReplies of size 0
    1331843818319: Inside UPLOAD_PHOTO!!
    1331843818320: Upload Photo succeeded
    1331843818320: Update in leader UI through StatusActivity:
    1331843818349: Received PROC_REQUEST 10:1 (0,0)->(0,0), replying PROC_REPLY 10:1 (0,0)->(0,0)
    1331843818350: Dispatching Atom PROC_REPLY 10:1 (0,0)->(0,0)
    1331843818350: I got a CSM packet at a leader

    1331843824546: Inside mux Packet.CLIENT_REQUEST
    1331843824546: Inside CLIENT_NEW_PHOTO!!
    1331843824553: Sending PROC_REQUEST 10:2 (0,0)->(0,0)
    1331843824567: Dispatching Atom PROC_REQUEST 10:2 (0,0)->(0,0)
    1331843824568: I got a CSM packet at a leader

    1331843824610: Show photo from server_show_newphoto
    1331843824577: Sending UDP payload: 3952
    1331843824592: removed replies before id 2 from sentReplies of size 0
    1331843824594: Inside UPLOAD_PHOTO!!
    1331843824596: Upload Photo succeeded
    1331843824596: Update in leader UI through StatusActivity:
    1331843824606: Received PROC_REQUEST 10:2 (0,0)->(0,0), replying PROC_REPLY 10:2 (0,0)->(0,0)
    1331843824607: Dispatching Atom PROC_REPLY 10:2 (0,0)->(0,0)


    ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~ ~

    1331843827814: Picture successfully taken, ORIG BYTE LENGTH = 1795916
    1331843828910: Our new height x width: 240 x 320
    1331843828923: BYTE SIZE AFTER COMPRESSION: 4836
    1331843828923: Inside mux Packet.CLIENT_REQUEST
    1331843828923: Inside CLIENT_NEW_PHOTO!!
    1331843828924: Sending PROC_REQUEST 10:3 (0,0)->(0,0)
    1331843828924: Dispatching Atom PROC_REQUEST 10:3 (0,0)->(0,0)
    1331843828936: Sending UDP payload: 5648
    1331843828939: Inside UPLOAD_PHOTO!!
    1331843828940: Upload Photo succeeded
    1331843828941: Update in leader UI through StatusActivity:
    1331843828947: Received PROC_REQUEST 10:3 (0,0)->(0,0), replying PROC_REPLY 10:3 (0,0)->(0,0)
    1331843828947: Dispatching Atom PROC_REPLY 10:3 (0,0)->(0,0)

    1331843830406: Inside mux Packet.CLIENT_REQUEST
    1331843830407: Inside CLIENT_NEW_PHOTO!!
    1331843830407: Sending PROC_REQUEST 10:4 (0,0)->(0,0)
    1331843830408: Dispatching Atom PROC_REQUEST 10:4 (0,0)->(0,0)
    1331843830438: Show photo from server_show_newphoto
    1331843830413: Sending UDP payload: 4826
    1331843830415: removed replies before id 4 from sentReplies of size 0
    1331843830416: Inside UPLOAD_PHOTO!!
    1331843830418: Upload Photo succeeded
    1331843830418: Update in leader UI through StatusActivity:
    1331843830437: Received PROC_REQUEST 10:4 (0,0)->(0,0), replying PROC_REPLY 10:4 (0,0)->(0,0)
    1331843830437: Dispatching Atom PROC_REPLY 10:4 (0,0)->(0,0)
    

Abnormalities
-----

Increase boundary region? Observed changes where the x rotated calculation of GPS:  `214, 185, 177, 144`,  with changes of 29, 8, and 33 meters!

    ~ ~ normal behavior of going into boundaries first and then change regions
    1331843181571: rotated x, y: 214.653547190019, 322.2270870707629
    1331843181571: location PINPOINTS to region = 4.0, previous -1
    1331843181571: Location CHANGED, but changed > 1 regions

    1331843271185: rotated x, y: 185.4974379195527, 293.76705250441574
    1331843271185: location PINPOINTS to region = 3.0, previous -1
    1331843271185: Location CHANGED, but changed > 1 regions

    1331843291155: rotated x, y: 177.02594992833843, 274.0295350365452
    1331843291156: location PINPOINTS to region = 3.0, previous -1
    1331843291156: Location CHANGED, but changed > 1 regions

    1331843531370: rotated x, y: 144.23217029069156, 43.33453023736877
    1331843531370: location PINPOINTS to region = 2.0, previous -1
    1331843531370: Location CHANGED, but changed > 1 regions
    ~ ~ and normal behavior afterwards



3871672
-------

Abnormalities
-----

* UI Mistake The user have not gone back to region 0 yet to reset and took a picture when the region was not set to 1-6. 

    1331843878361: location PINPOINTS to region = 1.0, previous -1
    1331843878362: Location CHANGED, but changed > 1 regions, so location is changed, trying to jump from region -1 to region 1
    1331843878723: Inside mux Packet.CLIENT_REQUEST
    1331843882146: Picture successfully taken, ORIG BYTE LENGTH = 1715157
    1331843883206: Our new height x width: 240 x 320
    1331843883207: Show photo from handle my camera take
    1331843883207: client making photo packet to send to leader
    1331843883221: BYTE SIZE AFTER COMPRESSION: 4182
    ~ ~ 3 GPS ~~
    1331843889395: location PINPOINTS to region = 1.0, previous -1
    1331843889397: Location CHANGED, but changed > 1 regions, so location is changed, trying to jump from region -1 to region 1
    1331843891350: .......... GPS

3905773
-------

User took two pictures in a row, in region 1 (we have not written code to see if the pictures were successfully uploaded yet)

1331844001399: Picture successfully taken, ORIG BYTE LENGTH = 1666913
1331844003638: Picture successfully taken, ORIG BYTE LENGTH = 1687427

10
====

2812221
-------

Changed to region 12074

    1331843852050: location PINPOINTS to region = 0.0, previous 0
    1331843852050: stay at region 0
    1331843855758: Inside mux Packet.CLIENT_REQUEST
    1331843856175: moving from region (0,0), to (12074,0)
    1331843856181: region (12074, 0) out of bounds, dormant
    1331843859075: .......... GPS onLocationChanged ...... 
    1331843859075: INSIDE DETERMINELOCATION
    1331843859076: Loc = Location[mProvider=gps,mTime=1331930260000,mLatitude=42.3581241252256,mLongitude=- 71.09290210449356,mHasAltitude=true,mAltitude=-16.0,mHasSpeed=true,mSpeed=0.06645944,mHasBearing=true,  mBearing=24.848696,mHasAccuracy=true,mAccuracy=10.0,mExtras=Bundle[mParcelledData.dataSize=4]] Previous Region = (12074,0)
    ...
    1331843859076: location PINPOINTS to region = 0.0, previous 12074

4074380
-------

The entire time as leader of region 1

    1331844556444: now fully up as LEADER (id=5010) of (1,0)

    1331844564529: Picture successfully taken, ORIG BYTE LENGTH = 1225189
    1331844565572: BYTE SIZE AFTER COMPRESSION: 3087 
    1331844565573: Inside mux Packet.CLIENT_REQUEST
    1331844565573: Inside CLIENT_NEW_PHOTO!!
    1331844565573: Sending PROC_REQUEST 10:4 (1,0)->(1,0)
    1331844565588: Sending UDP payload: 3899

    1331844607693: Picture successfully taken, ORIG BYTE LENGTH = 1511799
    1331844608759: BYTE SIZE AFTER COMPRESSION: 3711
    1331844608759: Inside mux Packet.CLIENT_REQUEST
    1331844608759: Inside CLIENT_NEW_PHOTO!!
    1331844608760: Sending PROC_REQUEST 10:6 (1,0)->(1,0)
    1331844608762: Sending UDP payload: 4523

    /* a non-leader took a pic */
    1331844615254: Inside mux Packet.CLIENT_REQUEST
    1331844615254: Inside CLIENT_NEW_PHOTO!!
    1331844615258: Sending PROC_REQUEST 10:7 (1,0)->(1,0)
    1331844615287: Sending UDP payload: 4063

4704357
-------

Accidentally opened cloud app.

112
====

4133022
-------

Lots of pictures and processing :)

    1331844150832: now fully up as LEADER (id=5112) of (0,0)
    1331844175577: now fully up as LEADER (id=5112) of (1,0)
    1331844220002: now fully up as LEADER (id=5112) of (2,0)
    1331844323263: now fully up as LEADER (id=5112) of (1,0)
    1331844364883: now NONLEADER (id=5112) following LEADER (id=5009) in (0,0)
    1331844397565: now fully up as LEADER (id=5112) of (1,0)
    1331844465479: now fully up as LEADER (id=5112) of (2,0)
    1331844518075: now NONLEADER (id=5112) following LEADER (id=5003) in (1,0)
    1331844525522: now fully up as LEADER (id=5112) of (1,0)
    1331844642680: now fully up as LEADER (id=5112) of (2,0)
    1331844683860: now fully up as LEADER (id=5112) of (3,0)

470870
------

Switched to cloud

2
=====

3827238
-------

1331843857404: now fully up as LEADER (id=5002) of (0,0)
1331843995187: now fully up as LEADER (id=5002) of (3,0)

4016919
-------

1331844159148: now fully up as LEADER (id=5002) of (0,0)
1331844348692: now NONLEADER (id=5002) following LEADER (id=5112) in (1,0)
1331844383751: now NONLEADER (id=5002) following LEADER (id=5005) in (0,0)
1331844419714: now NONLEADER (id=5002) following LEADER (id=5003) in (1,0)
1331844400236: I'm not a leader, requesting photos packet send out to leader
1331844412029: I'm not a leader, requesting photos packet send out to leader
1331844499230: now NONLEADER (id=5002) following LEADER (id=5007) in (0,0)
1331844502986: now NONLEADER (id=5002) following LEADER (id=5007) in (0,0)

4555890
-------

1331844583010: now NONLEADER (id=5002) following LEADER (id=5007) in (0,0)
1331844586743: now NONLEADER (id=5002) following LEADER (id=5007) in (0,0)

3
=====

2937989
-------

1331843718505: now NONLEADER (id=5003) following LEADER (id=5001) in (0,0)
1331843834224: now fully up as LEADER (id=5003) of (1,0)

3870535
-------
1331843944473: now NONLEADER (id=5003) following LEADER (id=5006) in (0,0)
1331843974174: now NONLEADER (id=5003) following LEADER (id=5006) in (0,0)
1331844007678: now fully up as LEADER (id=5003) of (0,0)
1331844016872: now fully up as LEADER (id=5003) of (1,0)

4217272
--------
1331844248414: now fully up as LEADER (id=5003) of (0,0)
1331844340172: now NONLEADER (id=5003) following LEADER (id=5112) in (1,0)

4366102
-------
1331844371703: now NONLEADER (id=5003) following LEADER (id=5009) in (0,0)
1331844414958: now fully up as LEADER (id=5003) of (1,0)

7
======


4416312
-------

Picture on region 1, but neither leader nor nonleader

    1331844526002: Picture successfully taken, ORIG BYTE LENGTH = 1424124
    1331844527043: Our new height x width: 240 x 320 
    1331844527044: Show photo from handle my camera take
    1331844527044: client making photo packet to send to leader
    1331844527066: BYTE SIZE AFTER COMPRESSION: 2545
    1331844527066: Inside mux Packet.CLIENT_REQUEST
    1331844527162: HI I'm in ONPAUSE()

4556378
-------

Ends with not a leader or nonleader, got client_request. I think the user accidentally got into ONPAUSE()

    1331844612355: Inside mux Packet.CLIENT_REQUEST
    1331844612413: HI I'm in ONPAUSE()
    1331844612674: .......... GPS onLocationChanged ...... 

4617389
-------

Ends with not a leader or nonleader, took picture

    1331844671513: Picture successfully taken, ORIG BYTE LENGTH = 1590143
    1331844672616: Our new height x width: 240 x 320 
    1331844672617: Show photo from handle my camera take
    1331844672617: client making photo packet to send to leader
    1331844672640: BYTE SIZE AFTER COMPRESSION: 4116
    1331844672641: Inside mux Packet.CLIENT_REQUEST
    1331844672716: HI I'm in ONPAUSE()

March 22 testing
-------------

Testing in Stata with just 2 phones and all except 1 of the 10 leader responses came before the timeout of 1 second (1000ms > 3*300ms of time between each retry). This is the log of that one time that it came too late, with the telltale message `cloud rejected leadership request`

    1332455545994: sending LEADER_REQUEST
    1332455545994: inside sendPacket(Packet p)
    1332455546023: Sending UDP payload: 426
    1332455546296: sending LEADER_REQUEST
    1332455546296: inside sendPacket(Packet p)
    1332455546299: Sending UDP payload: 426
    1332455546597: sending LEADER_REQUEST
    1332455546597: inside sendPacket(Packet p)
    1332455546600: Sending UDP payload: 426
    1332455546898: sending LEADER_REQUEST
    1332455546899: inside sendPacket(Packet p)
    1332455546902: Sending UDP payload: 426
    1332455547004: LEADER_REQUEST timed out
    1332455547396: cloud rejected leadership request or request failed, wait to retry
    1332455547404: Received UDP payload: 37185
    1332455547411: heard LEADER_REPLY from 5012
    1332455547413: now NONLEADER (id=5001) following LEADER (id=5012) in (1,0)

Time for leader to respond `547411 - 545994 = 1417 ms` > Time for timeout `547004 - 545994 = 1010`
