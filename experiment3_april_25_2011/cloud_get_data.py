import os
import re
import math

cloud_take_latency = []
cloud_get_latency = []

cloud_takeNum = 0 
cloud_takeGoodSave = 0 
cloud_takeBad = 0 
cloud_takeException = 0

cloud_getNum = 0 
cloud_getGood = 0
cloud_getBad = 0
cloud_getException = 0

def dirWalk(dirname):
    
    global cloud_take_latency
    global cloud_get_latency
    global cloud_takeNum
    global cloud_takeGoodSave
    global cloud_takeBad 
    global cloud_takeException
    global cloud_getNum 
    global cloud_getGood
    global cloud_getBad
    global cloud_getException

    cloud_take_latency = []
    cloud_get_latency = []

    cloud_takeNum = 0 
    cloud_takeGoodSave = 0 
    cloud_takeBad = 0 
    cloud_takeException = 0

    cloud_getNum = 0 
    cloud_getGood = 0
    cloud_getBad = 0
    cloud_getException = 0

    for (path, dirs, files) in os.walk(dirname):
        for filename in files:
            last_count_line = ""

            # get latency from the middle of the files
            for line in open(os.path.join(path, filename)):
                n = re.search("CameraCloud Execute HTTP (.*) latency: (.*)ms", line)
                if n is not None:
                    num = int(n.group(2))
                    if n.group(1) == "Upload":
                        cloud_take_latency.append(num) 
                    else: # == "Download"
                        cloud_get_latency.append(num)

                if "takeNum" in line:
                    last_count_line = line

            if 'last_count_line' != "":
                #reg=2 regionWidth=20.0 hyst=0.0 takeNum=29 takeCamGood=29 takeGoodSave=29 takeBad=0 takeException=0 takePercent=100% getNum=33 getGood=33 getBad=0 getException=0 getPercent=100%
                clcounts = re.search("reg=(.*) regionWidth=(.*) hyst=(.*) takeNum=(.*) takeCamGood=(.*) takeGoodSave=(.*) takeBad=(.*) takeException=(.*) takePercent=(.*) getNum=(.*) getGood=(.*) getBad=(.*) getException=(.*) getPercent", last_count_line)
                if clcounts is not None:

                    # get this line's values

                    takeNum = int(clcounts.group(4))
                    takeGoodSave = int(clcounts.group(6))
                    takeBad = int(clcounts.group(7))
                    takeException = int(clcounts.group(8))

                    getNum = int(clcounts.group(10))
                    getGood = int(clcounts.group(11))
                    getBad = int(clcounts.group(12))
                    getException = int(clcounts.group(13))

                    # add to current counters

                    cloud_takeNum += takeNum
                    cloud_takeGoodSave += takeGoodSave
                    cloud_takeBad += takeBad
                    cloud_takeException += takeException
                    
                    cloud_getNum += getNum
                    cloud_getGood += getGood 
                    cloud_getBad += getBad 
                    cloud_getException += getException

def stdvCalc(list):
    cumu = 0
    mean = sum(list)/len(list)
    for item in list:
        cumu += (mean-item)*(mean-item)
    return math.sqrt(cumu/len(list))

def printResults():
    print
    print "--------- TAKEs -------------" 
    print "number:\t\t%d" % cloud_takeNum
    print "successes:\t%d\t%d %%" % (cloud_takeGoodSave, (cloud_takeGoodSave*100/cloud_takeNum))
    print "fails:\t\t%d successful replies but saving failed" % (cloud_takeBad - cloud_takeException)
    print "exceptions:\t%d didn't complete cloud request" % cloud_takeException
    print "-- Latency --"
    print "number:\t\t%d" % len(cloud_take_latency)
    print "mean:\t\t%d ms" % (sum(cloud_take_latency)/len(cloud_take_latency))
    print "stdv:\t\t%d ms" % stdvCalc(cloud_take_latency)
    cloud_take_latency_s = sorted(cloud_take_latency)
    print "median:\t\t%d ms" % cloud_take_latency_s[len(cloud_take_latency)/2]
    print "range:\t\t%d ms ~ %d ms" % (cloud_take_latency_s[0], cloud_take_latency_s[-1])

    print
    print "--------- GETs ---------------" 
    print "number:\t\t%d" % cloud_getNum
    print "successes:\t%d\t%d %%" % (cloud_getGood, (cloud_getGood*100/cloud_getNum))
    print "fails:\t\t%d due to null region or region doesn't have photo" % (cloud_getBad - cloud_getException)
    print "exceptions:\t%d didn't complete cloud request" % cloud_getException
    print "-- Latency --"
    print "number:\t\t%d" % len(cloud_get_latency)
    print "mean:\t\t%d ms" % (sum(cloud_get_latency)/len(cloud_get_latency))
    print "stdv:\t\t%d ms" % stdvCalc(cloud_get_latency)
    cloud_get_latency_s = sorted(cloud_get_latency)
    print "median:\t\t%d ms" % cloud_get_latency_s[len(cloud_get_latency)/2]
    print "range:\t\t%d ms ~ %d ms" % (cloud_get_latency_s[0], cloud_get_latency_s[-1])

    print
    print "--------- BOTH --------------" 
    totalNum = cloud_takeNum + cloud_getNum
    print "number:\t\t%d" % totalNum
    totalSuccess = cloud_takeGoodSave + cloud_getGood
    print "successes:\t%d\t%d %%" % (totalSuccess, (totalSuccess*100/totalNum))
    print "-- Latency --"
    total = cloud_take_latency[:]
    total.extend(cloud_get_latency)
    print "number:\t\t%d" % len(total)
    print "mean:\t\t%d ms" % (sum(total)/len(total))
    print "stdv:\t\t%d ms" % stdvCalc(total)
    total_s = sorted(total)
    print "median:\t\t%d ms" % total_s[len(total)/2]
    print "range:\t\t%d ms ~ %d ms" % (total_s[0], total_s[-1])


if __name__ == "__main__":
    print "#################################"
    print "###### All of Cloud's Data ######"
    print "#################################"
    print
    dirname = "experiment3_logmsgs/nexus"
    dirWalk(dirname)
    print "============================="
    print "====== Cloud Nexus =========="
    print "============================="
    printResults()
    print
    dirname = "experiment3_logmsgs/notes"
    dirWalk(dirname)
    print "============================="
    print "====== Cloud Notes =========="
    print "============================="
    printResults()
