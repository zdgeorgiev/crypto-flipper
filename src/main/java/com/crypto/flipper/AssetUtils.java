package com.crypto.flipper;

import org.apache.commons.lang3.text.StrSubstitutor;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class AssetUtils {

	private static final Logger LOGGER = LoggerFactory.getLogger(AssetUtils.class);

	private static final String SHAPESHIFT_COINS_URL = "https://shapeshift.io/getcoins";

	private static Set<String> assets = new HashSet<>();

	static {
		loadAssetsFromConfigFile();
	}

	public static void main(String[] args) {
		loadAssetsFromConfigFile();
	}

	private static void loadAssetsFromConfigFile() {

		LOGGER.info("Initializing assets..");

		try {
			JSONObject coins = new JSONObject(getResponse(SHAPESHIFT_COINS_URL));
			coins.toMap().forEach((k, v) -> assets.add(k.toLowerCase()));
			LOGGER.info("Available assets : {}", assets);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static String getAsset(String name) {

		if (!assets.contains(name.toLowerCase()))
			throw new IllegalArgumentException("Application doesnt support [" + name + "] asset.");

		return name;
	}

	public static String getOrderPayload(String buyAsset, String sellAsset, String withdrawAddress, String returnAddress)
			throws IOException {

		InputStream in = AssetUtils.class.getResourceAsStream("/genericPayload.json");
		BufferedReader reader = new BufferedReader(new InputStreamReader(in));

		StringBuilder genericPayload = new StringBuilder();
		String line;

		while ((line = reader.readLine()) != null) {
			genericPayload.append(line);
		}

		Map<String, String> properties = new HashMap<>();
		properties.put("input-asset", getAsset(buyAsset));
		properties.put("output-asset", getAsset(sellAsset));
		properties.put("withdrawal-address", withdrawAddress);
		properties.put("return-address", returnAddress);

		StrSubstitutor strSubstitutor = new StrSubstitutor(properties);

		return strSubstitutor.replace(genericPayload);
	}

	private static String getResponse(String url) throws IOException {
		HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();

		con.setConnectTimeout(5000);
		con.setRequestProperty("User-Agent", "Mozilla/5.0");
		con.setRequestMethod("GET");

		try (BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()))) {

			String inputLine;
			StringBuilder content = new StringBuilder();

			while ((inputLine = in.readLine()) != null) {
				content.append(inputLine);
			}

			con.disconnect();

			return content.toString();
		}
	}
}
