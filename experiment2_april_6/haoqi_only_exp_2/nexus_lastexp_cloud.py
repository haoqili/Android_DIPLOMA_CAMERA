import os
import re

uplat = []
getlat = []

uplat_worse = []
getlat_worse = []

cloud_requests_take_total = 0 
cloud_requests_take_success = 0 
cloud_requests_take_bad = 0 
cloud_requests_get_total = 0 
cloud_requests_get_success = 0
cloud_requests_get_bad = 0

def dirWalk(dirname):
    files = os.listdir(dirname)
    tmp = ""

    global cloud_requests_take_total
    global cloud_requests_take_success
    global cloud_requests_take_bad 
    global cloud_requests_get_total 
    global cloud_requests_get_success
    global cloud_requests_get_bad

    for filename in files:
        last_count_line = ""
        for line in open(os.path.join(dirname, filename)):
            # TODO: change it to new code when running new code exp
            a = re.search("CameraCloud Execute HTTP latency: (.*)ms", line)
            n = re.search("CameraCloud (.*) photo latency = (.*)", line)
            if a is not None:
                tmp = a.group(1)
            
            if n is not None:
                if n.group(1) == "download":
                    getlat.append(int(tmp))
                    getlat_worse.append(int(n.group(2)))
                else: # == "upload new"
                    uplat.append(int(tmp)) 
                    uplat_worse.append(int(n.group(2)))

            if "takeNum" in line:
                last_count_line = line

        if 'last_count_line' != "":
            # TODO: change it new counts
            m = re.search("takeNum=(.*) takeCamGood=(.*) takeGoodSave=(.*) takeBad=(.*) getNum=(.*) getGood=(.*) getBad=(.*)", last_count_line)
            if m is not None:
                takeNum = int(m.group(1))
                takeCamGood = int(m.group(2))
                takeGoodSave = int(m.group(3))
                takeBad = int(m.group(4))
                getNum = int(m.group(5))
                getGood = int(m.group(6))
                getBad = int(m.group(7))

                cloud_requests_take_total += takeNum
                cloud_requests_take_success += takeGoodSave
                cloud_requests_take_bad += takeBad
                
                cloud_requests_get_total += getNum
                cloud_requests_get_success += getGood 
                cloud_requests_get_bad += getBad 

if __name__ == "__main__":
    dirname = "last_exp/cloud"
    dirWalk(dirname)
    print "Nexus last experiment Cloud"
    print "---------------------------"

    print
    print "TAKE a picture"
    print "count take requests: %d" % cloud_requests_take_total
    print "count take successes: %d" % cloud_requests_take_success
    print "count take bad: %d" % cloud_requests_take_bad
    print "Take requests with latencies: %d, i.e. these ones don't have any http exceptions" % len(uplat)
    print "Take mean: %d" % (sum(uplat)/len(uplat))
    print "Take mean: %d, when I measured it at a worse place" % (sum(uplat_worse)/len(uplat_worse))
    uplat_s = sorted(uplat)
    print "Take median: %d" % uplat_s[len(uplat)/2]
    uplat_worse_s = sorted(uplat_worse)
    print "Take median: %d, when I measured it at a worse place" % uplat_worse_s[len(uplat)/2]

    print
    print "GET a picture"
    print "count get requests: %d" % cloud_requests_get_total
    print "count get successes: %d" % cloud_requests_get_success
    print "count get bad: %d" % cloud_requests_get_bad
    print "Get requests with latencies: %d, i.e. these ones don't have any http exceptions" % len(getlat)
    print "Get mean: %d" % (sum(getlat)/len(getlat))
    print "Get mean: %d, when I measured it at a worse place" % (sum(getlat_worse)/len(getlat_worse))
    getlat_s = sorted(getlat)
    print "Get median: %d" % getlat_s[len(getlat)/2]
    getlat_worse_s = sorted(getlat_worse)
    print "Get median: %d, when I measured it at a worse place" % getlat_worse_s[len(getlat)/2]

    print
    print "TOTAL"
    total = uplat[:]
    total.extend(getlat)
    print "Total requests: %d" % len(total)
    print "Total mean: %d" % (sum(total)/len(total))
    total_worse = uplat_worse[:]
    total_worse.extend(getlat_worse)
    print "Total mean: %d, when I measured it at a worse place" % (sum(total_worse)/len(total_worse))
    print "Total median: %d" % total[len(total)/2]
    print "Total median: %d, when I meansured it at a worse place" % total_worse[len(total)/2]
