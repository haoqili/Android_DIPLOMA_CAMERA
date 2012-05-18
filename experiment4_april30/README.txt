Runs
=============
The times are derived from the server logs
Run 0
10:10am

Run 1
10:22 am

Run 2
10:39 am
10:49 am

Run 3
We forgrot to save run 3's logs

Run 4
11:05 am


Logs
=============

The changes made in logs:

* 10/csm_dip-1335792865983.txt's first 5013 lines, which came before OnResume(), were deleted because they were messing up the midtime runAssign().
* 10/ 10/csm-1333600752243.txt's first few lines without timestamps were deleted

----------

diploma_get_analysis has the checks that check for out-of-run-range samples commented out, but diploma_take_analysis have them running. Neither reported any out-of-run-range measurements, i.e. you can't find any "BADDDDDDDDDDDDDDDDDDD"s (good job volunteers for following instructions to quit/restart the apps!) <3

-------
Email sent by HaoQi May 3rd:

Hi,

We now have a script to sort the logfiles into the runs. The files are
sorted into this output:

t8_finalized.txt
where the first column are the assigned runs and the last 2 columns
indicate which file it is.
Don't be alarmed about the two "-1" assigned because:
# 5/csm_dip-1335792866157.txt and 17/1335792885337 return -1
#       but we can ignore it since it is always onPause()
#       so there are no clicks or getNums or anything useful in those two files

I have went through the output line by line to make sure that there is
0-4 for every phone (log/# indicate different # of phones) and found
out for one of them the assignment messed up due to the phone being in
onPause() for a long time before the experiment. I fixed it by just
deleting everything before the onResume(). 

Finally, the t8_finalized.txt is produced

