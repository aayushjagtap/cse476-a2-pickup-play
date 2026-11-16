## LakeKit after

### Baseline refresh on a fast network
PASS  Fast refresh works: 'Ludington: 1439 readings'
      screenshot 1_main_after_refresh.png

### Issue 1 (A): Refresh on a slow network (emulator GSM speed, GPRS latency)
      'Latest day only' switch toggled within 2.5 s of tapping it: True
      screenshot 2_slow_refresh_during.png
      ANR dialog seen: False
      refresh finished after ~10 s: 'Ludington: 241 readings'
      logcat:
```
11-16 08:25:55.638 I/LakeKitMeasure(13993): refresh click handler held the main thread for 0 ms
```

### Issue 1 (A): leave mid-download (rotate and Back) on the slow network
PASS  No crash after rotating and leaving during a refresh

### Issue 3 (C): accessibility labels on icon buttons
      surveyButton: content-desc='Open survey view'
      aboutButton: content-desc='About this app'
      screenshot 3_main_icons.png

### Issue 2 (B) feature check: times in the list still format
      first list times: ['Nov 16, 08:18', 'Nov 16, 08:12', 'Nov 16, 08:06']
PASS  Reading times formatted as 'MMM d, HH:mm'
      refreshed line: 'Refreshed 1 min ago (Nov 16, 08:25)'

### Issue 4 (A): Survey sweep for 10 s
      SweepView decode log (last lines):
```
11-16 08:27:11.388 I/LakeKitMeasure(14676): SweepView decoded sweep_grid 540 times; 1048576 bytes each
11-16 08:27:12.388 I/LakeKitMeasure(14676): SweepView decoded sweep_grid 600 times; 1048576 bytes each
11-16 08:27:13.387 I/LakeKitMeasure(14676): SweepView decoded sweep_grid 660 times; 1048576 bytes each
```
      GC log lines in 10 s: 0
      Total frames rendered: 599
      Janky frames: 2 (0.33%)
      screenshot 4_survey.png

### Export still works
      export file: total 156
-rw------- 1 u0_a194 u0_a194     24 2026-11-16 08:26 profileInstalled
-rw------- 1 u0_a194 u0_a194 109956 2026-11-16 08:26 station.json
-rw------- 1 u0_a194 u0_a194  31505 2026-11-16 08:27 survey-export.csv
      exportButton content-desc='Export readings as CSV'
      screenshot 5_about.png
PASS  No crash in logcat
