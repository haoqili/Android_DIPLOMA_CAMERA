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
diplo_requests_get_total = 0
diplo_requests_get_success = 0

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
		diplo_requests_take_success = diplo_requests_take_success + takeGoodSave

		diplo_requests_get_total = diplo_requests_get_total + getNum
		diplo_requests_get_success = diplo_requests_get_success + getGood

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



print "diplo-take\t%5d\t%5d" % (diplo_requests_take_total, diplo_requests_take_success)
print "diplo-get\t%5d\t%5d" % (diplo_requests_get_total, diplo_requests_get_success)

print "cloud-take\t%5d\t%5d" % (cloud_requests_take_total, cloud_requests_take_success)
print "cloud-get\t%5d\t%5d" % (cloud_requests_get_total, cloud_requests_get_success)
