Logs
=============
DO NOT USE ME TO RUN ANALYSIS SCRIPTS, use logs instead. Only use this directory to see the original files.

The changes made in logs:

* 10/csm_dip-1335792865983.txt's first 5013 lines, which came before OnResume(), were deleted because they were messing up the midtime runAssign().
* 10/ 10/csm-1333600752243.txt's first few lines without timestamps were deleted

----------

diploma_get_analysis has the checks that check for out-of-run-range samples commented out, but diploma_take_analysis have them running. Neither reported any out-of-run-range measurements, i.e. you can't find any "BADDDDDDDDDDDDDDDDDDD"s (good job volunteers for following instructions to quit/restart the apps!) <3
