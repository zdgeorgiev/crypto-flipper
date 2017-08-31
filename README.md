<h2>Crypto-Flipper</h2>

Crypto-flipper is console app that find arbitrage orders on Shapeshift.

Disclaimer

__USE THE SOFTWARE AT YOUR OWN RISK. YOU ARE RESPONSIBLE FOR YOUR OWN MONEY__
__THE AUTHORS AND ALL AFFILIATES ASSUME NO RESPONSIBILITY FOR YOUR TRADING RESULTS.__


<h2>Requirements :</h2> 

1) Java 8
2) Maven (https://maven.apache.org/download.cgi) If you want to build it yourself.
3) Internet connection

<h2>HOW TO RUN :</h2>

1) Download latest version from releases

OR

* Build manually with `mvn clean install` in this directory

2) Run in terminal 

`java -jar [place to set the mandatory system params] target/crypto-flipper.jar` (system params can be set like this -Dparam.name=param.value)

OR 

* reconfigure `run-flipper.sh` file with  your params, make sure the script is executable and run it with `./run-flupper.sh` from this directory

<h2> System Parameters </h2>

***
Mandatory params :

`flipper.buy.amount` : Amount you want to trade.

`flipper.sell.asset` : Asset you want to trade.

`flipper.buy.asset` : Asset you want to receive.

`flipper.withdrawal.address` : Address where you want to receive the buy asset.

`flipper.refund.address` : Refund address where you will receive sell asset if any shapeshift rules are not fulfilled (Read ShapeShift Terms and Conditions on https://shapeshift.io)

***
Optional params :
 
`flipper.min.profit` : DEFAULT - 0.0. You can se the default profit you aim for + two transaction fees you have to provide (X -> Y and Y -> Z)

`flipper.logs.dest` : DEFAULT - Directory where you run the app. Path directory where you want to save the logs.

`flipper.min.offer.time.left.minute` : DEFAULT - 5. Keep eye of offers that have at least X minutes left to be fulfilled.

`flipper.request.timeout.seconds` : DEFAULT - 3. Timeout between each request in seconds.

`flipper.debug` : DEFAULT - false. Verbose logs.
