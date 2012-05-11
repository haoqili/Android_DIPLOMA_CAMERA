Motivation/background
--------------------

    * The need for DIPLOMA
    * How DIPLOMA works
    * Goal: implement DIPLOMA on a Panoramio-like app
        * Expected improvements over traditional cloud-Panoramio-like app

Panoramio-like app UI/functionality 
--------------------
(identical to DIPLOMA app and CLOUD app)

    * Regions
    * Take picture
    * Get picture
    * Timeouts

The DIPLOMA app
---------------

    * Client requests go to leader
    * Leader saves new picture or forwards get request through DIPLOMA
    * Leader replies with
    * Latency recorded on client when it hears the leader reply
    * Point out where acks and multiple tries occur
        * Outside DIPLOMA: First leg, and last leg
        * Inside DIPLOMA: leader-to-leader
    * Different kinds of results: good, bad, timedout, etc.

The CLOUD app
------------

    * Each take/get goes to cloud directly
    * Different kinds of results: good, bad, timedout, etc.

Experiments
-------

    * Expected results
    * Actual results
        * Include what is fixed in each trial
        * Include an analysis of results
        1.  failed due to crashing
        2.  central square – too many hot spots – phones couldn’t hear each other
        3.  77 mass ave – phones still couldn’t hear each other
        4.  77 mass ave with smaller regions – phones still couldn’t hear each other
        5.  indoors – 98% is the worst, otherwise 100%

Conclusion
-----------

    * This app shows how DIPLOMA can improve latency in smart-phones, given these premise:
    * Given 
        * phones have a wide enough wifi range 
        * solid wifi communication
        * enough users to fill all the regions
        * these conditions might be true in the future
