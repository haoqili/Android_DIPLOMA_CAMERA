import os
import re
import math

# TODO: latency for this?

diploma_get_latency = []


diploma_getNum = 0 
diploma_getGood = 0
diploma_getBad = 0
diploma_getTimedout = 0

timeoutPeriod = 6000

def dirWalk(dirname):

    global diploma_get_latency
    global diploma_getNum 
    global diploma_getGood
    global diploma_getBad
    global diploma_getTimedout

    diploma_get_latency = []

    diploma_getNum = 0 
    diploma_getGood = 0
    diploma_getBad = 0
    diploma_getTimedout = 0

    for (path, dirs, files) in os.walk(dirname):
        for filename in files:
            last_count_line = ""

            # get latency from the middle of the files
            for line in open(os.path.join(path, filename)):
                """
                g = re.search("download remote photo latency = (.*)", line)
                if g is not None:
                    get_latency = int(g.group(1))
                    # there is a case when the app might got paused
                    # and start latency didn't get set
                    # so it records latency as "1335382548303" and "1335382549047"
                    # I have checked that all other latencies are:
                    if get_latency < 1000000:
                        diploma_get_latency.append(get_latency)
                """
                if "regionWidth=10" in line:
                    last_count_line = line

            if 'last_count_line' != "":
                #reg=0 id=5104 state=2 regionWidth=20.0 hyst=0.0 takeNum=12 takeCamGood=12 takeGoodSave=7 takeBad=1 takeTimedout=3 takePercent=58% getNum=15 getGood=3 getBad=8 getTimedout=4 getPercent=20%
                dipcounts = re.search("reg=(.*) id=(.*) state=(.*) regionWidth=10(.*) hyst=(.*) takeNum=(.*) takeCamGood=(.*) takeGoodSave=(.*) takeBad=(.*) takeTimedout=(.*) takePercent=(.*) getNum=(.*) getGood=(.*) getBad=(.*) getTimedout=(.*) getPercent", last_count_line)
                if dipcounts is not None:

                    # get this line's values

                    takeNum = int(dipcounts.group(6))
                    takeGoodSave = int(dipcounts.group(8))

                    getNum = int(dipcounts.group(12))
                    getGood = int(dipcounts.group(13))
                    getBad = int(dipcounts.group(14))
                    getTimedout = int(dipcounts.group(15))

                    # add to current counters
                    
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
    
    print
    print "--------- GETs ---------------"
    print "number:\t\t%d" % diploma_getNum
    print "successes:\t%d\t%d %%\tincluding existing regions without a photo" % (diploma_getGood, (diploma_getGood*100/diploma_getNum))
    print "fails:\t\t%d due to null region" % diploma_getBad
    print "timed outs:\t%d requests that take longer than 6 seconds, some don't ever have responses" % diploma_getTimedout
    #latencyPrints(diploma_get_latency)

if __name__ == "__main__":
    print "################################################"
    print "###### Diploma with Region Width = 10 ##########"
    print "################################################"
    print "takes about 3 secs to run for me"
    print
    dirname = "experiment3_logmsgs/nexus"
    dirWalk(dirname)
    print "============================="
    print "====== Diploma Nexus ========"
    print "============================="
    printResults()
    print
    print
    print "Diploma Notes didn't use region width 10"
