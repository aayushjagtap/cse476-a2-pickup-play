## LakeKit before

### Baseline refresh on a fast network
PASS  Fast refresh works: 'Ludington: 1440 readings'
      screenshot 1_main_after_refresh.png

### Issue 1 (A): Refresh on a slow network (emulator GSM speed, GPRS latency)
      'Latest day only' switch toggled within 2.5 s of tapping it: False
      screenshot 2_slow_refresh_during.png
      ANR dialog seen: False
      refresh finished after ~42 s: 'Refresh failed: timeout'
      logcat:
```
11-16 08:24:26.192 I/LakeKitMeasure(13057): refresh click handler held the main thread for 31755 ms
11-16 08:24:01.458 I/WindowManager(  521): ANR in Window{e6fa2d1 u0 edu.msu.cse476.lakekit/edu.msu.cse476.lakekit.MainActivity}. Reason:Input dispatching timed out (e6fa2d1 edu.msu.cse476.lakekit/edu.msu.cse476.lakekit.MainActivity (server) is not responding. Waited 5003ms for MotionEvent(deviceId=-1, eventTime=730366000000, source=TOUCHSCREEN, displayId=0, action=DOWN, actionButton=0x00000000, flags=0x00000000, metaState=0x00000000, buttonState=0x00000000, classification=NONE, edgeFlags=0x00000000, xPrecision=1.0, yPrecision=1.0, xCursorPosition=nan, yCursorPosition=nan, pointers=[0: (160.0, 335.4)]), policyFlags=0x6b000000)
11-16 08:24:02.017 E/ActivityManager(  521): ANR in edu.msu.cse476.lakekit (edu.msu.cse476.lakekit/.MainActivity)
11-16 08:24:02.017 E/ActivityManager(  521): Reason: Input dispatching timed out (e6fa2d1 edu.msu.cse476.lakekit/edu.msu.cse476.lakekit.MainActivity (server) is not responding. Waited 5003ms for MotionEvent(deviceId=-1, eventTime=730366000000, source=TOUCHSCREEN, displayId=0, action=DOWN, actionButton=0x00000000, flags=0x00000000, metaState=0x00000000, buttonState=0x00000000, classification=NONE, edgeFlags=0x00000000, xPrecision=1.0, yPrecision=1.0, xCursorPosition=nan, yCursorPosition=nan, pointers=[0: (160.0, 335.4)]), policyFlags=0x6b000000)
11-16 08:24:26.194 I/Choreographer(13057): Skipped 1904 frames!  The application may be doing too much work on its main thread.
```

### Issue 1 (A): leave mid-download (rotate and Back) on the slow network
FAIL  aborted: RuntimeError('display did not rotate to landscape')
      screenshot ERROR.png
