#! /bin/bash
#TODO: Replace echo "scale=20;  by bc for floating point stuff
rm *.data -rf
grep -R "json" * | cut -f 13 -d ' ' > diplo-totalCell-byteCounts.data
grep "Cloud server request" -R * | cut -d " " -f 6 > ccloudReqBytes.data
grep "Cloud response" -R * | cut -d ' ' -f 5 > ccloudResBytes.data
grep "Sending UDP payload" -R * | cut -d ' ' -f 5 > SentWifiDIPLOMA.data
grep "Received UDP payload" -R * | cut -d ' ' -f 5 > RxWifiDIPLOMA.data

# slope and intercepts from Excel


#*****************Cellular Tx************************
m=9.52092*10^-007
c=0.3430

sum=0
set -e
for line in $(cat ccloudReqBytes.data) ; do
    
     ((sum+=line))
done
bytes=$sum 

ccloudCellularTx=$bytes
echo "Tx Cellular CCloud   " $bytes

sum=0
for line in $(cat diplo-totalCell-byteCounts.data) ; do
    
     ((sum+=line))
done
bytes=$sum 

diploCellularTx=$bytes
echo "Tx Cellular Diploma  " $bytes


#**************************Cellular Rx****************
m=2.1825*10^-006	
c=0.3647

sum=0
for line in $(cat ccloudResBytes.data) ; do
    
     ((sum+=line))
done
bytes=$sum 

ccloudCellularRx=$bytes
echo "Rx Cellular CCloud  " $bytes

#***********************With tails ********************

#*****************Cellular Tx tail***************
m=4.088*10^-006
c=3.1767
sum=0
for line in $(cat ccloudReqBytes.data) ; do
    
     ((sum+=line))
done
bytes=$sum 

ccloudCellularTx=$bytes
echo "Tx Cellular CCloud  " $bytes

sum=0
for line in $(cat diplo-totalCell-byteCounts.data) ; do
    
     ((sum+=line))
done
bytes=$sum 

diploCellularTx=$bytes
echo "Tx Cellular Diploma  " $bytes

#****************Cellular Rx tail******************
m=4.2684*10^-006	
c=2.93119
sum=0
for line in $(cat ccloudResBytes.data) ; do
    
     ((sum+=line))
done
bytes=$sum 

ccloudCellularRx=$bytes
echo "Rx Cellular CCloud  " $bytes

#**************8Totals ********
diploTotal=$diploCellularTx
ccloudTotal=`echo "scale=20;  $ccloudCellularTx + $ccloudCellularRx" | bc`

echo "DIPLOMA total bytes" $diploTotal
echo "CCloud total bytes" $ccloudTotal
