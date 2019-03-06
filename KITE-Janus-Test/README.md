#  KITE-Janus-Test

This sample test script is designed for load testing. It creates **N** meeting rooms on Janus SFU server and connect **K** users to each room.
It tests against a modified Janus videoroom plugin web app.  

The size of the load test is determined by the number of Chrome instances available on the Grid. In our sample config file, we assume that the grid has only 6 Chrome instance available.
In order to run large load test, Cosmo has created a commercial tool, the KITE Grid Manager, which generates very large grids with thousands of Chrome instances.
Please contact Cosmo <contact@cosmosoftware.io> for more info. 

## Test Script

The load testing happens in two phases where two scripts are executed:  
-	Ramp up phase, when the browsers are launched, and participants join the meeting rooms.
-	Load reached phase, when all participants have joined their room and the target load has been reached. During this phase, the script allows to let participant leave and rejoin the rooms creating a programmed and reproducible stress test scenarios.

### Ramp-up Script

Sample script for ramp-up:
1.	Open URL mymeeting.com/meetingID
Meeting ID will be provided by the RoomManager. Every **K** participants, depending on the model in the config file.
2.	Each participant will wait until all others are connected to meeting.
3.	Check the published video
4.	Check received videos from all participants
5.	GetStats on all the peerConnections
6.	Take a screenshot
7.	Stay in the meeting until the end of the test

### Load reached Script

This test script verifies for each participant that the video has not been lost, when the target load has been reached:
1.	Check of the published video.
2.	Check received videos from all participants. 
3.	GetStats on all the peerConnections
4.	Take a screenshot
5.  Reconnects the participant at random or pre-defined intervals
6.  End and restart meeting rooms at random interval (all participant of that meeting room leaves)

## Pre-requisite: Selenium Grid

To run this test you will need a Selenium Grid with at least **K** instance of Chrome.

## Config
 
 A sample config file is provided at  
 
 `configs/local.janus.config.json`  

### Important parameters 

Set the address of your Selenium Hub:  
  `"remoteAddress": "http://localhost:4444/wd/hub"`  
  
Set your Chrome version and OS according to what is available on your Grid. The test has been written specifically for Chrome. Some functionality will not work on other web browsers.
```
"browsers": [
    {
      "browserName": "chrome",
      "version": "72",
      "platform": "WINDOWS",
      "headless": false
    }
  ]
```


Set the total number of participants (= number of Chrome instances):  
`"tupleSize": 6`  

Set the **K** number of participant in each meeting room:  
`"usersPerRoom": 2`  

With this setting, KITE will create **N**=3 meeting rooms with **K**=2 participants in each meeting.  


### Report parameters

Whether to generate a CSV report file  
`"csvReport": true`  

Whether to generate a json report file  
`"jsonReport": true`  

Whether to take screenshot for each test/client (if false, it will still take screenshot in case of failure)     
`"takeScreenshotForEachTest": true`  


### GetStats parameters

Whether to call getStats  
`"getStats": true`  

How long to collect stats for (in seconds)  
`"statsCollectionTime" : 4`  

Interval between 2 getStats calls (in seconds)  
`"statsCollectionInterval" : 2`  



### Load Reached parameters

How long to remain in the 'load reached' stage (in seconds)  
`"loadReachTime" : 300`  


During load reach phase, each client stay in the room for a random duration between `reconnectionDelay`s and  (`reconnectionDelay`s +`reconnectionRandom`s).
With the following settings, each client will reconnect (refresh the page), after a random duration between 60s and 90s:  
`"reconnectionDelay" : 60`  
`"reconnectionRandom" : 30`  

During load reach phase, every `checkStatusPeriod`s, there is a 1/`chanceOfMeetingEnded` that a given meeting is ended.
When a meeting is ended, all the participant will leave the meeting and reconnect.
With the following settings, every 60s, there will be a 1% chance that a meeting is ended:  
`"chanceOfMeetingEnded": 100`  
`"checkStatusPeriod": 60`  



You should not need to change any other parameter.


## Compile

Under the root directory:  
``` 
mvn -DskipTests clean install 
``` 

## Run

Under the KITE-Janus-Test/ folder, execute:  
```
java -cp ../KITE-Engine/target/kite-jar-with-dependencies.jar;../KITE-Common/target/kite-common-1.0-SNAPSHOT.jar;../KITE-Engine-IF/target/kite-if-1.0-SNAPSHOT.jar;target/Janus-test-1.0-SNAPSHOT.jar org.webrtc.kite.Engine configs/local.janus.config.json
```


## Test output

Each test run will create a new folder under `KITE-Janus-Test/results/` with the follwing subfolders:
* `consoleLogs/` two files per client, one for the ramp-up script, one for the load reached script, with the console logs (only works with Chrome)
* `getstats/` will contain one json file with the full getStats object for each getStats call 
* `reports/` the json reports
* `screenshots/` the screenshots

The test will also output two CSV reports:  
* `rureport_<TIMESTAMP>.csv`  
* `lrreport_<TIMESTAMP>.csv`  





