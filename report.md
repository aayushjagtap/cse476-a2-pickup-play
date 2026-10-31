### A2 iteration: sport picker in phone landscape
PASS  All six sports visible at once in landscape (no scrolling)
      screenshot a2_iteration_sport_picker_landscape.png

### Capability failure: permission refused
PASS  Nearest first chip is on the session list
PASS  No distances before the feature is used
PASS  No permission prompt at launch or on opening the list
      screenshot a3_0_list_before.png
PASS  System permission dialog appears when the chip is tapped
      screenshot a3_1_permission_dialog.png
PASS  Deny button found
PASS  Denied message shown: 'Location permission denied. Distances are unavailable, so sessions are shown in the normal order. Tap Nearest first to try again.'
PASS  Chip turns back off after denial
PASS  No distances shown after denial
      screenshot a3_2_permission_denied.png
PASS  App still usable: detail screen opens after denial

### Rationale, then grant, then live input
PASS  Rationale dialog shown on second attempt
      screenshot a3_3_rationale.png
PASS  System dialog shown again after rationale
PASS  Active status: 'Sorted nearest first. Location accurate to about 5 m.'
PASS  At Beaumont Tower, nearest is Chill shootaround: ['Chill shootaround', 'Friday night runs']
PASS  Cards show distance and Walkable band
      screenshot a3_4_sorted_beaumont.png
PASS  After moving to IM East, list re-sorts: ['Competitive 5v5', 'Chill shootaround']
      screenshot a3_5_sorted_im_east.png

### Edge condition: implausible location jump
PASS  Jump ignored: 'Ignored a sudden 77 mi location jump. Keeping your last location until the new one holds steady for a few seconds.'
PASS  Order unchanged while jump is unconfirmed: ['Competitive 5v5', 'Chill shootaround']
PASS  Distances still from the last good location while the jump is unconfirmed
      screenshot a3_6_jump_ignored.png
PASS  Jump confirmed after holding steady: 'Your location moved 77 mi and held steady, so distances were updated.'
FAIL  Confirmation message still readable a few seconds later
PASS  Distances updated to Detroit (Drive band)
      screenshot a3_7_jump_confirmed.png

### Rotation keeps the feature on
PASS  Chip still on in landscape
PASS  Distances still shown in landscape
      screenshot a3_8_landscape.png

### Location turned off (capability unavailable)
PASS  Location-off message: 'Location is turned off on this device, so distances are unavailable. Turn it on in Settings to sort by distance.'
PASS  Settings button offered
PASS  List still shown
      screenshot a3_9_location_off.png
PASS  No crash in logcat

**1 failure(s)**
