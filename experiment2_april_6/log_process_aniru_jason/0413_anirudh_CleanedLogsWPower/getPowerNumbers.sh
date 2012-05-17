#! /bin/bash
#TODO: Replace echo "scale=20;  by bc for floating point stuff
rm *.data -rf
grep -R "json" * | cut -f 13 -d ' ' > diplo-totalCell-byteCounts.data
grep "Cloud server request" -R * | cut -d " " -f 6 > ccloudReqBytes.data
grep "Cloud response" -R * | cut -d ' ' -f 5 > ccloudResBytes.data
grep "Sending UDP payload" -R * | cut -d ' ' -f 5 > SentWifiDIPLOMA.data
grep "Received UDP payload" -R * | cut -d ' ' -f 5 > RxWifiDIPLOMA.data

# slope and intercepts from Excel


#************************wifi Tx*********************
m=1.2419*10^-005	
c=0.008

sum=0
for line in $(cat SentWifiDIPLOMA.data) ; do
     packetEnergy=`echo "scale=20;  $m * $line + $c" | bc `
     sum=`echo "scale=20; $sum + $packetEnergy" | bc`
done
energy=$sum 
diploWifiTx=$energy
echo "Tx Wifi milli Joules " $energy
 
#******************wifi Rx*****************************
m=3.28656*10^-006	
#c=-0.005
c=0

sum=0
for line in $(cat RxWifiDIPLOMA.data) ; do
    packetEnergy=`echo "scale=20;  $m * $line + $c" | bc `
     sum=`echo "scale=20; $sum + $packetEnergy" | bc`
done
energy=$sum 
diploWifiRx=$energy
echo "Rx Wifi milli Joules " $energy

#*****************Cellular Tx************************
m=9.52092*10^-007
c=0.3430

sum=0
set -e
for line in $(cat ccloudReqBytes.data) ; do
    packetEnergy=`echo "scale=20;  $m * $line + $c" | bc `
     sum=`echo "scale=20; $sum + $packetEnergy" | bc`
done
energy=$sum 

ccloudCellularTx=$energy
echo "Tx Cellular CCloud  milli Joules " $energy

sum=0
for line in $(cat diplo-totalCell-byteCounts.data) ; do
    packetEnergy=`echo "scale=20;  $m * $line + $c" | bc `
     sum=`echo "scale=20; $sum + $packetEnergy" | bc`
done
energy=$sum 

diploCellularTx=$energy
echo "Tx Cellular Diploma milli Joules " $energy


#**************************Cellular Rx****************
m=2.1825*10^-006	
c=0.3647

sum=0
for line in $(cat ccloudResBytes.data) ; do
    packetEnergy=`echo "scale=20;  $m * $line + $c" | bc `
     sum=`echo "scale=20; $sum + $packetEnergy" | bc`
done
energy=$sum 

ccloudCellularRx=$energy
echo "Rx Cellular CCloud milli Joules " $energy

diploNoTotal=`echo "scale=20;  $diploCellularTx + $diploWifiTx + $diploWifiRx" | bc`
ccloudNoTotal=`echo "scale=20;  $ccloudCellularTx + $ccloudCellularRx" | bc`

#***********************With tails ********************

#*****************Cellular Tx tail***************
m=4.088*10^-006
c=3.1767
sum=0
for line in $(cat ccloudReqBytes.data) ; do
    packetEnergy=`echo "scale=20;  $m * $line + $c" | bc `
     sum=`echo "scale=20; $sum + $packetEnergy" | bc`
done
energy=$sum 

ccloudCellularTx=$energy
echo "Tx Cellular CCloud milli Joules " $energy

sum=0
for line in $(cat diplo-totalCell-byteCounts.data) ; do
    packetEnergy=`echo "scale=20;  $m * $line + $c" | bc `
     sum=`echo "scale=20; $sum + $packetEnergy" | bc`
done
energy=$sum 

diploCellularTx=$energy
echo "Tx Cellular Diploma milli Joules " $energy

#****************Cellular Rx tail******************
m=4.2684*10^-006	
c=2.93119
sum=0
for line in $(cat ccloudResBytes.data) ; do
    packetEnergy=`echo "scale=20;  $m * $line + $c" | bc `
     sum=`echo "scale=20; $sum + $packetEnergy" | bc`
done
energy=$sum 

ccloudCellularRx=$energy
echo "Rx Cellular CCloud milli Joules " $energy

#**************8Totals ********
diploTotal=`echo "scale=20;  $diploCellularTx + $diploWifiTx + $diploWifiRx" | bc`
ccloudTotal=`echo "scale=20;  $ccloudCellularTx + $ccloudCellularRx" | bc`

echo "Without tail "
echo "DIPLOMA total energy" $diploNoTotal
echo "CCloud total energy" $ccloudNoTotal

echo "With tail"
echo "DIPLOMA total energy" $diploTotal
echo "CCloud total energy" $ccloudTotal
