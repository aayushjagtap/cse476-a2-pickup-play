### Row 1: light level in lux follows the environment
PASS  Shows 312 lx after the light changes to 312
PASS  Shows 45 lx after the light changes to 45
      screenshot row01_light_45lx.png

### Row 2: light sensor released when leaving the main Activity
PASS  Sensor service lists LakeLog's light listener while the main Activity is visible

### Row 6 (part 1): observer name

### Row 3 + 7: Readings opens, downloads, lists time and level
PASS  Light listener released after opening Readings
PASS  Readings listed: first rows [('Nov 16 08:06 UTC', '176.353 m', False), ('Nov 16 08:00 UTC', '176.353 m', False), ('Nov 16 07:54 UTC', '176.353 m', False)]
PASS  Each row shows a level in meters or 'Unavailable'
      summary: 479 readings in the last 48 hours, newest first
      screenshot row07_readings_list.png
PASS  Note Activity shows the selected time Nov 16 08:06 UTC
PASS  Note Activity shows the level 176.353 m
PASS  Note Activity shows the observer's name (row 6)
PASS  A different row opens its own reading (Nov 16 08:00 UTC)

### Row 5: unsaved text survives rotation
PASS  Unsaved note text kept in landscape
      screenshot row05_note_landscape.png
PASS  Unsaved note text kept after rotating back

### Row 4: Save marks the reading
PASS  After Save the reading is marked: ['Nov 16 08:06 UTC']
      screenshot row04_marked.png

### Row 12: Back (button and system) discards; empty save deletes
PASS  Reopening shows the saved note (row 9)
PASS  Back button discarded the edit
PASS  System Back discarded the edit

### Row 10: notes survive a refresh
PASS  After refresh the note is still marked: Nov 16 08:06 UTC, 176.353 m, has a note

### Row 9 + 6: notes and name survive force-stop and relaunch
PASS  Observer name restored after relaunch
PASS  Note still marked after relaunch
PASS  Note text restored after relaunch

### Row 12 (part 2): saving a blank note deletes it
PASS  Blank save removed the mark

### Row 2 (part 2): sensor released in the background
PASS  Light listener active again on the main Activity
PASS  Light listener released after pressing Home

### Row 7: slow network stays responsive, leaving mid-download doesn't crash
PASS  Shows 'Downloading…' on the slow network
      screenshot row07_downloading_slow.png
PASS  Back during the download returns at once (3.1s)
PASS  No crash after leaving mid-download
FAIL  List finishes downloading on the slow network

### Row 8: no network reports failure; Retry recovers
PASS  Failure reported with no network
PASS  Retry button shown
      screenshot row08_failed.png
PASS  Retry downloads and lists readings after the network returns

### Row 11: scroll to the end
PASS  Scrolled to the end; oldest visible reading Nov 14 08:24 UTC
      screenshot row11_list_end.png
PASS  No crash in logcat during the functional run

### Row 11: every screen at font scale 2.0, phone and tablet, both orientations
      screenshot font2_phone_port_1_main.png
PASS  font2_phone_port: main controls all reachable
      screenshot font2_phone_port_2_readings.png
PASS  font2_phone_port: Readings list and Refresh visible (6 rows on screen)
      screenshot font2_phone_port_3_note.png
PASS  font2_phone_port: note controls all reachable
      screenshot font2_phone_land_1_main.png
PASS  font2_phone_land: main controls all reachable
      screenshot font2_phone_land_2_readings.png
PASS  font2_phone_land: Readings list and Refresh visible (4 rows on screen)
      screenshot font2_phone_land_3_note.png
PASS  font2_phone_land: note controls all reachable
      screenshot font2_tablet_port_1_main.png
PASS  font2_tablet_port: main controls all reachable
      screenshot font2_tablet_port_2_readings.png
PASS  font2_tablet_port: Readings list and Refresh visible (12 rows on screen)
      screenshot font2_tablet_port_3_note.png
PASS  font2_tablet_port: note controls all reachable
      screenshot font2_tablet_land_1_main.png
PASS  font2_tablet_land: main controls all reachable
      screenshot font2_tablet_land_2_readings.png
PASS  font2_tablet_land: Readings list and Refresh visible (9 rows on screen)
      screenshot font2_tablet_land_3_note.png
PASS  font2_tablet_land: note controls all reachable
PASS  No crash in logcat

**1 failure(s)**
