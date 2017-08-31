package com.crypto.flipper;

import org.apache.commons.io.FileUtils;
import org.joda.time.DateTime;
import org.joda.time.Period;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.stream.Collectors;

public class CryptoFlipper {

	// ============Parameters============

	private static final Double BUY_AMOUNT = Double.parseDouble(System.getProperty("flipper.buy.amount"));

	private static final String SELL_ASSET = System.getProperty("flipper.sell.asset");
	private static final String BUY_ASSET = System.getProperty("flipper.buy.asset");

	private static final String WITHDRAW_ADDRESS = System.getProperty("flipper.withdrawal.address");
	private static final String RETURN_ADDRESS = System.getProperty("flipper.refund.address");

	private static final Double MIN_PROFIT = Double.parseDouble(System.getProperty("flipper.min.profit", "0.0"));

	private static final String LOGS_DEST = System.getProperty("flipper.logs.dest", new File("").getAbsolutePath());

	private static final int MIN_OFFER_TIME_LEFT = Integer.parseInt(System.getProperty("flipper.min.offer.time.left.minute", "5"));

	private static final int REQUEST_SECONDS_TIMEOUT = Integer.parseInt(System.getProperty("flipper.request.timeout.seconds", "3"));

	private static final String LOG_LEVEL = System.setProperty("flipper.logging.level",
			System.getProperty("flipper.debug", "false").equals("false") ?
					"INFO" : // Normal logs
					"DEBUG"  // Debug logs
	);

	// ==================================

	private static final Logger LOGGER = LoggerFactory.getLogger(CryptoFlipper.class);

	private static URL SHAPESHIFT_URL;

	static {
		try {
			SHAPESHIFT_URL = new URL("https://shapeshift.io/sendAmount");
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}
	}

	private static File ALL_ORDERS_DESTINATION;
	private static File ALL_ARBS_DESTINATION;

	private static final String TRANSACTION_BASE_URL = "https://shapeshift.io/#/status/";

	private static String BUY_PAYLOAD;
	private static String SELL_PAYLOAD;

	private static final String ORDER_ID = "orderId";
	private static final String QUOTED_RATE = "quotedRate";
	private static final String WITHDRAWAL_AMOUNT = "withdrawalAmount";

	private static final DateTimeFormatter ORDER_DTF = DateTimeFormat.forPattern("MM-dd-yyyy-HH:mm:ss");

	// Here we will keep the past entries that are not older than 5 minutes
	// At the top will be the transaction with lower buy value and longest time left
	private static BlockingQueue<CryptoOrder> buyOrders = new PriorityBlockingQueue<>(50, (o1, o2) -> {
		if (o1.getOrderRatio() != o2.getOrderRatio()) {
			return Double.compare(o2.getOrderRatio(), o1.getOrderRatio());
		}

		return o2.getTimestamp().compareTo(o1.getTimestamp());
	});

	public static void main(String[] args) throws IOException, InterruptedException {

		initializeArguments();

		int ordersCount = 0;

		SoundUtils.beep(150, 500);
		LOGGER.info("Flipper successfully started..");

		while (true) {

			try {
				clearOldBuyOrders();

				LOGGER.debug("----");
				LOGGER.debug("BUY_PAYLOAD: {}", String.format(BUY_PAYLOAD, BUY_AMOUNT));
				CryptoOrder currentBuyOrder = createOrderFromResponse(String.format(BUY_PAYLOAD, BUY_AMOUNT));

				// Current buy order must be added in the queue
				buyOrders.add(currentBuyOrder);

				double tokensToSell = buyOrders.peek().getWithdrawalAmount();

				LOGGER.debug("SELL_PAYLOAD: {}", String.format(SELL_PAYLOAD, tokensToSell));
				CryptoOrder currentSellOrder = createOrderFromResponse(String.format(SELL_PAYLOAD, tokensToSell));

				saveCurrentOrders(currentBuyOrder, currentSellOrder);

				CryptoOrder bestBuyOrder = buyOrders.peek();

				BigDecimal profit = BigDecimal.valueOf(currentSellOrder.getWithdrawalAmount())
						.subtract(BigDecimal.valueOf(BUY_AMOUNT));

				// Only the offer that has any potential profit when subtract the transaction fees
				if (profit.compareTo(BigDecimal.valueOf(MIN_PROFIT)) > 0) {

					// Make a sound so you can run it in the background without checking
					SoundUtils.beep(350, 500);

					String arbitrage = arbToString(bestBuyOrder, currentSellOrder, profit);

					LOGGER.info("Found arbitrage : " + arbitrage);

					saveCurrentArb(arbitrage);
				}

				if (++ordersCount % 100 == 0)
					LOGGER.info("Processed {} buy and sell orders", ordersCount * 2);

			} catch (Exception e) {
				// Don't care about the internal server errors etc..
				LOGGER.debug("Error: ", e);
			}

			Thread.sleep(REQUEST_SECONDS_TIMEOUT * 1000);
		}
	}

	private static void initializeArguments() throws IOException {

		BUY_PAYLOAD = AssetUtils.getOrderPayload(SELL_ASSET, BUY_ASSET, WITHDRAW_ADDRESS, RETURN_ADDRESS);
		SELL_PAYLOAD = AssetUtils.getOrderPayload(BUY_ASSET, SELL_ASSET, RETURN_ADDRESS, WITHDRAW_ADDRESS);

		if (!new File(LOGS_DEST).exists())
			throw new IllegalArgumentException("Invalid directory.. [" + LOGS_DEST + "]");

		final DateTimeFormatter FILE_NAMING_DTF = DateTimeFormat.forPattern("MM-dd-yyyy");

		String dayMonthYear = FILE_NAMING_DTF.print(new DateTime());

		ALL_ORDERS_DESTINATION = new File(LOGS_DEST, String.format("%s-%s-%s-orders.txt", dayMonthYear, SELL_ASSET, BUY_ASSET));
		ALL_ARBS_DESTINATION = new File(LOGS_DEST, String.format("%s-%s-%s-arbs.txt", dayMonthYear, SELL_ASSET, BUY_ASSET));

		LOGGER.info("============================");
		LOGGER.info("You trading {} -> {}", SELL_ASSET, BUY_ASSET);
		LOGGER.info("Buy amount: {}", BUY_AMOUNT);
		LOGGER.info("Min profit: {}", MIN_PROFIT);
		LOGGER.info("Withdraw address({}): {}", BUY_ASSET, WITHDRAW_ADDRESS);
		LOGGER.info("Return address({}): {}", SELL_ASSET, RETURN_ADDRESS);
		LOGGER.info("All orders will be saved at: {}", ALL_ORDERS_DESTINATION);
		LOGGER.info("Arbitrages will be saved at: {}", ALL_ARBS_DESTINATION);
		LOGGER.info("Logs level: {}", System.getProperty("flipper.logging.level"));
		LOGGER.info("============================");
	}

	private static void clearOldBuyOrders() {
		DateTime nowBeforeXMinutes = new DateTime().minusMinutes(MIN_OFFER_TIME_LEFT);

		List<CryptoOrder> youngestOrders = buyOrders.stream()
				.filter(c -> c.getTimestamp().isAfter(nowBeforeXMinutes))
				.collect(Collectors.toList());

		buyOrders.clear();
		buyOrders.addAll(youngestOrders);
	}

	private static CryptoOrder createOrderFromResponse(String payload) throws IOException {

		DateTime now = new DateTime();
		String content = getResponse(payload);

		try {
			JSONObject gnt_eth = new JSONObject(content);
			double orderRatio = Double.parseDouble(((HashMap) gnt_eth.toMap().get("success")).get(QUOTED_RATE) + "");
			String orderId = ((HashMap) gnt_eth.toMap().get("success")).get(ORDER_ID) + "";
			double withdrawalAmount = Double.parseDouble(((HashMap) gnt_eth.toMap().get("success")).get(WITHDRAWAL_AMOUNT) + "");

			return new CryptoOrder(orderId, now, orderRatio, withdrawalAmount);
		} catch (Exception e) {
			throw new IllegalStateException(content);
		}
	}

	private static String getResponse(String payload) throws IOException {
		HttpURLConnection con = (HttpURLConnection) SHAPESHIFT_URL.openConnection();

		con.setConnectTimeout(5000);
		con.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
		con.setRequestProperty("User-Agent", "Mozilla/5.0");
		con.setRequestMethod("POST");
		con.setDoOutput(true);
		con.setDoInput(true);

		OutputStream os = con.getOutputStream();
		os.write(payload.getBytes("UTF-8"));
		os.close();

		BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
		String inputLine;
		StringBuilder content = new StringBuilder();

		while ((inputLine = in.readLine()) != null) {
			content.append(inputLine);
		}
		in.close();

		con.disconnect();

		return content.toString();
	}

	private static void saveCurrentOrders(CryptoOrder buyOrder, CryptoOrder sellOrder) throws IOException {

		List<String> currentOrder = new ArrayList<>();

		currentOrder.add(ORDER_DTF.print(buyOrder.getTimestamp()) + " " +
				BigDecimal.valueOf(BUY_AMOUNT)
						.divide(BigDecimal.valueOf(buyOrder.getWithdrawalAmount()), 8, BigDecimal.ROUND_HALF_UP) +
				String.format(" %.8f", sellOrder.getOrderRatio()) + " " +
				buyOrder.getOrderId() + " " + sellOrder.getOrderId());

		FileUtils.writeLines(ALL_ORDERS_DESTINATION, currentOrder, true);
	}

	private static String arbToString(CryptoOrder buyOrder, CryptoOrder sellOrder, BigDecimal profit) {

		Period timeleft = new Period(sellOrder.getTimestamp(), buyOrder.getTimestamp().plusMinutes(10));

		return TRANSACTION_BASE_URL + buyOrder.getOrderId() + " " +
				TRANSACTION_BASE_URL + sellOrder.getOrderId() + " " +
				"TIMELEFT:" + timeleft.getMinutes() + "m" + timeleft.getSeconds() + "s " +
				"Ξ+" + profit;
	}

	private static void saveCurrentArb(String arbitrageString) throws IOException {
		List<String> currentOrder = new ArrayList<>();
		currentOrder.add(arbitrageString);
		FileUtils.writeLines(ALL_ARBS_DESTINATION, currentOrder, true);
	}
}
