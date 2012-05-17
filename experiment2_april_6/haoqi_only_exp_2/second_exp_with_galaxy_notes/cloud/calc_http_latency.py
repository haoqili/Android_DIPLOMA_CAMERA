import re

f = open('http_latency.txt', 'r')
lines = f.readlines()
sum = 0
count = 0
for line in lines: 
    count+=1
    num = re.findall(r'Execute HTTP latency: ([0-9]+)ms', line)
    sum += int(num[0])
print "total = ", sum
print "amount = ", len(lines)
print "average Cloud HTTP Latency Time for 4G Galaxy Notes = ", sum/len(lines), "ms"
