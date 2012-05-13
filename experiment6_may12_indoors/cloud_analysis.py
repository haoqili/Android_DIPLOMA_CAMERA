import os
import re
import math

# TODO: add combined times

# These are the timestamps of the first requests to the server
#       ordered by their run number
# The last value is the end of the last request of the last run
RUNTIMES = [1336840909254,
            1336842780000,
            1336843992709,
            1336845898135,
            1336847014221]

# the ith element is about run i (the ith time server restarts)

cloud_take_latency = [[], [], [], []]
cloud_takeNum = [0, 0, 0, 0]
cloud_takeGood = [0, 0, 0, 0]
cloud_takeBad = [0, 0, 0, 0]

cloud_get_latency = [[], [], [], []]
cloud_getNum = [0, 0, 0, 0]
cloud_getGood = [0, 0, 0, 0]
cloud_getBad = [0, 0, 0, 0]

timeoutPeriod = 6000

# different algorithm than experiment 5
def runAssign(time):
    for i in range(len(RUNTIMES)-1):
        if RUNTIMES[i] > time:
            return i-1
    return 3

# return only the first line of the file
def head(f):
  stdin,stdout = os.popen2("head -n 1 "+f)
  stdin.close()
  line = stdout.readlines(); stdout.close()
  return line

# return only the last line of the file
def tail(f):
  stdin,stdout = os.popen2("tail -n 1 "+f)
  stdin.close()
  line = stdout.readlines(); stdout.close()
  return line

def dirWalk(dirname):

    global cloud_take_latency
    global cloud_takeNum 
    global cloud_takeGood
    global cloud_takeBad

    global cloud_get_latency
    global cloud_getNum 
    global cloud_getGood
    global cloud_getBad

    cloud_take_latency = [[], [], [], []] 
    cloud_takeNum = [0, 0, 0, 0]
    cloud_takeGood = [0, 0, 0, 0]
    cloud_takeBad = [0, 0, 0, 0]  

    cloud_get_latency = [[], [], [], []] 
    cloud_getNum = [0, 0, 0, 0]
    cloud_getGood = [0, 0, 0, 0]
    cloud_getBad = [0, 0, 0, 0]  

    for (path, dirs, files) in os.walk(dirname):
        for filename in files:
            # skip diploma files
            if filename.startswith("csm_dip"):
                continue
            
            # get the last line of file
            tailine = tail(os.path.join(path, filename))
            tailtime = int(tailine[0].strip()[:13])

            # Continue if it's an old file 
            if tailtime < RUNTIMES[0]:
                continue

            # Put the files into run buckets based on tail timestamp
            runNumber = runAssign(tailtime) 
            #print
            #print runNumber, tailtime, path, filename

            ###################################################
            # get latency from the middle of the files
            tmp_latency = 0
            for line in open(os.path.join(path, filename)):
                # Number of takes clicked
                take_search = re.search("Clicked take picture button", line)
                if take_search is not None:
                    cloud_takeNum[runNumber] += 1
                # Number of gets clicked
                get_search = re.search("Clicked getphotos Button from region ", line)
                if get_search is not None:
                    cloud_getNum[runNumber] += 1

                # Retrieve latency info
                tlat = re.search("CameraCloud Execute HTTP Upload latency: (.*)ms", line)
                if tlat is not None:
                    tmp_latency = int(tlat.group(1))
                glat = re.search("CameraCloud Execute HTTP Download latency: (.*)ms", line)
                if glat is not None:
                    tmp_latency = int(glat.group(1))

                # t4. successfulness search
                tbad = re.search("one more takeBad", line)
                if tbad is not None:
                    cloud_takeBad[runNumber] += 1
                gbad = re.search("one more getBad", line)
                if gbad is not None:
                    cloud_getBad[runNumber] += 1

                tgood = re.search("one more takeGoodSave", line)
                if tgood is not None:
                    cloud_takeGood[runNumber] += 1
                    cloud_take_latency[runNumber].append(tmp_latency)
                ggood = re.search("one more getGood", line)
                if ggood is not None:
                    cloud_getGood[runNumber] += 1
                    cloud_get_latency[runNumber].append(tmp_latency)

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
    #print "-- Latency: raw, including timed outs --"
    #print "number:\t\t%d" % len(latency_list)
    #print "mean:\t\t%d ms" % (sum(latency_list)/len(latency_list))
    #print "stdv:\t\t%d ms" % stdvCalc(latency_list)
    latency_list_sorted = sorted(latency_list)
    #print "median:\t\t%d ms" % latency_list_sorted[len(latency_list)/2]
    #print "range:\t\t%d ms ~ %d ms" % (latency_list_sorted[0], latency_list_sorted[-1])
    print "-- Latency associated with successes --"
    latency_list_small = listWithinTimeout(latency_list_sorted)
    print "number:\t\t%d\texcluding %d requests that have replies > timeout" % (len(latency_list_small), len(latency_list)-len(latency_list_small))
    print "mean:\t\t%d ms" % (sum(latency_list_small)/len(latency_list_small))
    print "stdv:\t\t%d ms" % stdvCalc(latency_list_small)
    print "median:\t\t%d ms" % latency_list_small[len(latency_list_small)/2]
    print "range:\t\t%d ms ~ %d ms" % (latency_list_small[0], latency_list_small[-1])

def printResults():
    for iRun in range(len(RUNTIMES)-1):
        print
        print "======= Takes for run %d ========" % (iRun + 1)
        print "clicked:\t%d" % cloud_takeNum[iRun]
        print "successes:\t%d\t%d %%" % (cloud_takeGood[iRun], (cloud_takeGood[iRun]*100/cloud_takeNum[iRun]))
        print "fails:\t\t%d" % cloud_takeBad[iRun]
        print "timed outs:\t%d\tcalculated indirectly by number-successes-fails" % (cloud_takeNum[iRun]-cloud_takeGood[iRun]-cloud_takeBad[iRun])
        # only do latency when there are replies
        if (cloud_takeGood[iRun] + cloud_takeBad[iRun]) > 0:
            latencyPrints(cloud_take_latency[iRun])

        print
        print "======= GETs for run %d ========" % (iRun + 1)
        print "gets clicked:\t%d" % cloud_getNum[iRun]
        print "successes:\t%d\t%d %%\tincluding existing regions without a photo" % (cloud_getGood[iRun], (cloud_getGood[iRun]*100/cloud_getNum[iRun]))
        print "fails:\t\t%d\tdue to null region or no photo in region, but still with reply" % cloud_getBad[iRun]
        print "timed outs:\t%d\tcalculated indirectly by number-successes-fails" % (cloud_getNum[iRun]-cloud_getGood[iRun]-cloud_getBad[iRun])

        # only do latency when there are replies
        if (cloud_getGood[iRun] + cloud_getBad[iRun]) > 0:
            latencyPrints(cloud_get_latency[iRun])
        print

if __name__ == "__main__":
    dirname = "logs_sorted_bytime"
    dirWalk(dirname)
    print "#############################################"
    print "###### Cloud based on run number ############"
    print "#############################################"
    printResults()
