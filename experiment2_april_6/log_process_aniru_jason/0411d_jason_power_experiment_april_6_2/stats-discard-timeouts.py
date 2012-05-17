#!/usr/bin/env python
import sys
from numpy import *

values = map(int, sys.stdin.readlines())

values = list( filter((lambda x: x < 6000), values))

print values

arr = array(values)
print "lines\t%5d" % (len(arr))
print "mean\t%5d" % (arr.mean())
print "median\t%5d" % (median(arr))
print "std-dev\t%5d" % (arr.std())
