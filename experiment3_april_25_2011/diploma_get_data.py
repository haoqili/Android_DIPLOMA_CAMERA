import os
import re
import math

# add region width
# add region info. like how far away gets are made to
# leader vs nonleader

diploma_take_latency = []
diploma_get_latency = []

diploma_takeNum = 0 
diploma_takeGoodSave = 0 
diploma_takeBad = 0 
diploma_takeTimedout = 0

diploma_getNum = 0 
diploma_getGood = 0
diploma_getBad = 0
diploma_getTimedout = 0

timeoutPeriod = 6000

def dirWalk(dirname):

    global diploma_take_latency
    global diploma_get_latency
    global diploma_takeNum
    global diploma_takeGoodSave
    global diploma_takeBad 
    global diploma_takeTimedout
    global diploma_getNum 
    global diploma_getGood
    global diploma_getBad
    global diploma_getTimedout

    diploma_take_latency = []
    diploma_get_latency = []

    diploma_takeNum = 0 
    diploma_takeGoodSave = 0 
    diploma_takeBad = 0 
    diploma_takeTimedout = 0

    diploma_getNum = 0 
    diploma_getGood = 0
    diploma_getBad = 0
    diploma_getTimedout = 0

    for (path, dirs, files) in os.walk(dirname):
        for filename in files:
            last_count_line = ""

            upload_start = 0;
            # get latency from the middle of the files
            for line in open(os.path.join(path, filename)):
                # I accidentally deleted the line that set upload start
                # since only 1 request is processed at a time
                # this greps for the line that is within 2ms of upload_start
                startS = re.search("(.*): inside sendRequestPacketRepeatingRunnable for requestCount = ", line)
                if startS is not None:
                    upload_start = int(startS.group(1))

                n = re.search("upload new photo latency = (.*)", line)
                if n is not None:
                    difference = int(n.group(1)) - upload_start
                    diploma_take_latency.append(difference)
                    startS = 0 #optional reset startS 

                g = re.search("download remote photo latency = (.*)", line)
                if g is not None:
                    get_latency = int(g.group(1))
                    # there is a case when the app might got paused
                    # and start latency didn't get set
                    # so it records latency as "1335382548303" and "1335382549047"
                    # I have checked that all other latencies are:
                    if get_latency < 1000000:
                        diploma_get_latency.append(get_latency)

                if "takeNum" in line:
                    last_count_line = line

            if 'last_count_line' != "":
                #reg=0 id=5104 state=2 regionWidth=20.0 hyst=0.0 takeNum=12 takeCamGood=12 takeGoodSave=7 takeBad=1 takeTimedout=3 takePercent=58% getNum=15 getGood=3 getBad=8 getTimedout=4 getPercent=20%
                dipcounts = re.search("reg=(.*) id=(.*) state=(.*) regionWidth=(.*) hyst=(.*) takeNum=(.*) takeCamGood=(.*) takeGoodSave=(.*) takeBad=(.*) takeTimedout=(.*) takePercent=(.*) getNum=(.*) getGood=(.*) getBad=(.*) getTimedout=(.*) getPercent", last_count_line)
                if dipcounts is not None:

                    # get this line's values

                    takeNum = int(dipcounts.group(6))
                    takeGoodSave = int(dipcounts.group(8))
                    takeBad = int(dipcounts.group(9))
                    takeTimedout = int(dipcounts.group(10))

                    getNum = int(dipcounts.group(12))
                    getGood = int(dipcounts.group(13))
                    getBad = int(dipcounts.group(14))
                    getTimedout = int(dipcounts.group(15))

                    # add to current counters

                    diploma_takeNum += takeNum
                    diploma_takeGoodSave += takeGoodSave
                    diploma_takeBad += takeBad
                    diploma_takeTimedout += takeTimedout
                    
                    diploma_getNum += getNum
                    diploma_getGood += getGood 
                    diploma_getBad += getBad 
                    diploma_getTimedout += getTimedout

def stdvCalc(list):
    cumu = 0
    mean = sum(list)/len(list)
    for item in list:
        cumu += (mean-item)*(mean-item)
    return math.sqrt(cumu/len(list)) 

def listWithinTimeout(sortedList):
    global timeoutPeriod
    listWithin = sortedList[:] # make a copy first
    firstTimeoutI = len(sortedList)-1 #initialize to the biggest element
    for i in range(len(sortedList)):
        if sortedList[i] >= timeoutPeriod:
            # this is the first element bigger than timeoutPeriod
            return listWithin[:i]
    # all elements in list are smallerthan timeout
    return listWithin

def latencyPrints(latency_list):
    print "-- Latency: raw, including timed outs --"
    print "number:\t\t%d" % len(latency_list)
    print "mean:\t\t%d ms" % (sum(latency_list)/len(latency_list))
    print "stdv:\t\t%d ms" % stdvCalc(latency_list)
    latency_list_sorted = sorted(latency_list)
    print "median:\t\t%d ms" % latency_list_sorted[len(latency_list)/2]
    print "range:\t\t%d ms ~ %d ms" % (latency_list_sorted[0], latency_list_sorted[-1])
    print "-- Latency: excluding timed outs --"
    latency_list_small = listWithinTimeout(latency_list_sorted)
    print "number:\t\t%d\tis %d %% of all latencies.\t%d requests never have responses" % (len(latency_list_small), (len(latency_list_small)*100/(1.0*(len(latency_list)))), len(latency_list)-len(latency_list_small))
    print "mean:\t\t%d ms" % (sum(latency_list_small)/len(latency_list_small))
    print "stdv:\t\t%d ms" % stdvCalc(latency_list_small)
    print "median:\t\t%d ms" % latency_list_small[len(latency_list_small)/2]
    print "range:\t\t%d ms ~ %d ms" % (latency_list_small[0], latency_list_small[-1])

def printResults():
    print
    print "--------- TAKEs -------------"
    print "number:\t\t%d" % diploma_takeNum
    print "successes:\t%d\t%d %%" % (diploma_takeGoodSave, (diploma_takeGoodSave*100/diploma_takeNum))
    print "fails:\t\t%d successful replies but saving failed" % diploma_takeBad 
    print "timed outs:\t%d requests that take longer than 6 seconds, some don't ever have responses" % diploma_takeTimedout
    latencyPrints(diploma_take_latency)
    
    print
    print "--------- GETs ---------------"
    print "number:\t\t%d" % diploma_getNum
    print "successes:\t%d\t%d %%\tincluding existing regions without a photo" % (diploma_getGood, (diploma_getGood*100/diploma_getNum))
    print "fails:\t\t%d due to null region" % diploma_getBad
    print "timed outs:\t%d requests that take longer than 6 seconds, some don't ever have responses" % diploma_takeTimedout
    latencyPrints(diploma_get_latency)

    print
    print "--------- BOTH --------------"
    totalNum = diploma_takeNum + diploma_getNum
    print "number:\t\t%d" % totalNum
    totalSuccess = diploma_takeGoodSave + diploma_getGood
    print "successes:\t%d\t%d %%" % (totalSuccess, (totalSuccess*100/totalNum))
    total = diploma_take_latency[:]
    total.extend(diploma_get_latency)
    latencyPrints(total)

if __name__ == "__main__":
    print "#################################"
    print "###### All of Diploma's Data ####"
    print "#################################"
    print "takes about 20 secs to run for me"
    print
    dirname = "experiment3_logmsgs/nexus"
    dirWalk(dirname)
    print "============================="
    print "====== Diploma Nexus ========"
    print "============================="
    printResults()
    print
    print
    dirname = "experiment3_logmsgs/notes"
    dirWalk(dirname)
    print "============================="
    print "====== Diploma Notes ========"
    print "============================="
    printResults()
