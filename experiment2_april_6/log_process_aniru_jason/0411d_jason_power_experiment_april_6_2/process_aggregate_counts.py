#!/usr/bin/env python

import datetime
import os, sys
import re


cloud_requests_take_total = 0
cloud_requests_take_success = 0

cloud_requests_get_total = 0
cloud_requests_get_success = 0

diplo_requests_take_total = 0
diplo_requests_take_success = 0
diplo_requests_take_failures = 0

diplo_requests_get_total = 0
diplo_requests_get_success = 0
diplo_requests_get_failures = 0



diplo_requests_take_total = 0
diplo_requests_takeCamGood = 0
diplo_requests_takeGoodSave = 0
diplo_requests_takeBad = 0
diplo_requests_takeTimedout = 0
diplo_requests_getNum = 0
diplo_requests_getGood = 0
diplo_requests_getBad = 0
diplo_requests_getTimedout = 0




for line in sys.stdin.readlines():
	m_diplo = re.search('takeNum=(.*) takeCamGood=(.*) takeGoodSave=(.*) takeBad=(.*) takeTimedout=(.*) getNum=(.*) getGood=(.*) getBad=(.*) getTimedout=(.*)', line)
	m_cloud = re.search('takeNum=(.*) takeCamGood=(.*) takeGoodSave=(.*) takeBad=(.*) getNum=(.*) getGood=(.*) getBad=(.*)', line)
	
	if m_diplo is not None:
		takeNum = int(m_diplo.group(1))
		takeCamGood = int(m_diplo.group(2))
		takeGoodSave = int(m_diplo.group(3))
		takeBad = int(m_diplo.group(4))
		takeTimedout = int(m_diplo.group(5))
		getNum = int(m_diplo.group(6))
		getGood = int(m_diplo.group(7))
		getBad = int(m_diplo.group(8))
		getTimedout = int(m_diplo.group(9))
		
		
		diplo_requests_take_total = diplo_requests_take_total + takeNum 
		diplo_requests_takeCamGood = diplo_requests_takeCamGood + takeCamGood
		diplo_requests_takeGoodSave = diplo_requests_takeGoodSave + takeGoodSave
		diplo_requests_takeBad = diplo_requests_takeBad + takeBad
		diplo_requests_takeTimedout = diplo_requests_takeTimedout + takeTimedout
		diplo_requests_getNum = diplo_requests_getNum + getNum
		diplo_requests_getGood = diplo_requests_getGood + getGood
		diplo_requests_getBad = diplo_requests_getBad + getBad
		diplo_requests_getTimedout = diplo_requests_getTimedout + getTimedout
		
		diplo_requests_take_failures = diplo_requests_take_failures + takeTimedout
		# Note: takeBad triggered from UserApp.java:227, actually is a success, just means server ran out of space (which never happened)
		
		#diplo_requests_take_success = diplo_requests_take_success + takeGoodSave
		diplo_requests_get_total = diplo_requests_get_total + getNum
		diplo_requests_get_failures = diplo_requests_get_failures + getTimedout
		
		# Note: getBad triggered from UserApp.java:252, actually is a successs, just means there is no remote photo yet
		
		#diplo_requests_get_success = diplo_requests_get_success + getGood

	elif m_cloud is not None:
		takeNum = int(m_cloud.group(1))
		takeCamGood = int(m_cloud.group(2))
		takeGoodSave = int(m_cloud.group(3))
		takeBad = int(m_cloud.group(4))
		getNum = int(m_cloud.group(5))
		getGood = int(m_cloud.group(6))
		getBad = int(m_cloud.group(7))
		
		cloud_requests_take_total = cloud_requests_take_total + takeNum
		cloud_requests_take_success = cloud_requests_take_success + takeGoodSave
		
		cloud_requests_get_total = cloud_requests_get_total + getNum
		cloud_requests_get_success = cloud_requests_get_success + getGood


diplo_requests_take_total = diplo_requests_takeGoodSave + diplo_requests_takeBad + diplo_requests_takeTimedout
diplo_requests_get_total = diplo_requests_getGood + diplo_requests_getBad + diplo_requests_getTimedout

diplo_requests_take_success = diplo_requests_take_total - diplo_requests_take_failures
diplo_requests_get_success = diplo_requests_get_total - diplo_requests_get_failures

print "diplo-take\t%5d\t%5d" % (diplo_requests_take_total, diplo_requests_take_success)
print "diplo-get\t%5d\t%5d" % (diplo_requests_get_total, diplo_requests_get_success)

print "cloud-take\t%5d\t%5d" % (cloud_requests_take_total, cloud_requests_take_success)
print "cloud-get\t%5d\t%5d" % (cloud_requests_get_total, cloud_requests_get_success)



print diplo_requests_take_total , "diplo_requests_take_total"
print diplo_requests_takeCamGood , "diplo_requests_takeCamGood"
print diplo_requests_takeGoodSave , "diplo_requests_takeGoodSave"
print diplo_requests_takeBad , "diplo_requests_takeBad "
print diplo_requests_takeTimedout , "diplo_requests_takeTimedout"

print diplo_requests_getNum , "diplo_requests_getNum "
print diplo_requests_getGood , "diplo_requests_getGood "
print diplo_requests_getBad , "diplo_requests_getBad "
print diplo_requests_getTimedout , "diplo_requests_getTimedout"