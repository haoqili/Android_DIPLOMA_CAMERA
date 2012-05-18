Run 1

* 12:39 - 11:07am. Duration: 28minutes
* 4G
* Faster cloud get latency due to phone caching, because we pressed all the gets one after the other on one phone, i.e: 
    Phone 1 Get 1, Phone 1 Get 2, Phone 1 Get 3 .... Phone 2 Get 1, Phone 2 Get 2, Phone 2 Get 3 ...
* Sequence

    * Take real pics, and get all pictures
    * Take black pics, and get all pictures
    * Take real pics, and get all pictures
    * Take black pics, and get all pictures

Run 2

* Server restart 1:13, taking pictures start: 1:17. End: 1:23 ish. Duration 6-11 minutes, let's say, 10
* 3G
* Faster cloud get latency due to phone caching, because we pressed all the gets one after the other on one phone, i.e: 
    Phone 1 Get 1, Phone 1 Get 2, Phone 1 Get 3 .... Phone 2 Get 1, Phone 2 Get 2, Phone 2 Get 3 ...
    Until the very last few gets when we discovered the method that avoids phone caching (see below)
* Sequence

    * Take real pics, and get all pictures
    * Take black pics, and get all pictures
    * At end, could not get due to leader in region 0 getting GPS fixes so it changed to a dormant region. So we stopped the run immediately and turned off the GPS on all phones

* The server logs for this run is not logged

Run 3

* 1:32 - 1:58. Duration: 26 minutes
* 3G
* Normal cloud get latency without phone caching, because we left enough gap between gets on each phone:
    Phone 1 Get 1, Phone 2 Get 1, Phone 3 Get 1 .... Phone 1 Get 2, Phone 2 Get 2, Phone 3 Get 2 ...
* Sequence

    * Take real pics, and get all pictures
    * Take black pics, and get all pictures
    * Take black pics, and get all pictures
    * Take black pics, and get all pictures

* Anirudh had some bad gets, but when he tried again, they worked. Maybe that phone was going through a barnacle app breakup, or the wifi was not good.

Run 4
    
* 2:03 - 2:24. Duration: 21 minutes
* 4G
* Everything same as Run 3

Anirudh:
HaoQi and I did another static experiment to see what caused the
impressive 3G GET performance last time. What is happening is this :

When one cellular GET is followed by subsequent cellular GETs in quick
succession, the subsequent GETs are much faster.

This doesn't have anything to do with caching; it's probably related
to the state machine of the cellular radio. I used the term cellular
because we see this for 3G and 4G. To avoid this 'warm-up' effect, we
changed out access pattern to do :

Reg0 Get on phone 1, Reg0 Get on phone 2.... , Reg0 Get on phone 10,
Reg1 Get on phone 1 instead of :

Reg0 Get on phone 1, Reg1 Get on phone 1.... , Reg5 Get on phone 1,
Reg0 Get on phone 2.

This gives us the expected similar gain over 3G for both GETs and
TAKEs that we saw last time only for TAKEs. The gain over 4G improves
as well. Also, this time we placed the phones further apart (actually
5 m unlike about 3.75 m last time). Besides, we placed them face down
on chairs, without mounting them (face down is a slightly worse
condition for propagation). Overall, we still get  ~ 100 %  on DIPLOMA
as well.

Some high level summaries :

1. We used 6 regions here unlike the indoor walking experiment with 4
regions. We still got better results. My suspicion is that range isn't
as much of an issue as mobility.
[HaoQi: or because we had the replyCounter bug.]

2. When HaoQi and I moved from one phone to another, we were
effectively doing our own form of medium access, eliminating
collisions. Collisions would have been more likely with people doing
GET's and TAKEs asynchronously.
