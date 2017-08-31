package com.crypto.flipper;

import org.joda.time.DateTime;

public class CryptoOrder {

	private String orderId;
	private DateTime timestamp;
	private double orderRatio;
	private double withdrawalAmount;

	public CryptoOrder(String orderId, DateTime timestamp, double orderRatio, double withdrawalAmount) {
		setOrderId(orderId);
		setTimestamp(timestamp);
		setOrderRatio(orderRatio);
		setWithdrawalAmount(withdrawalAmount);
	}

	public DateTime getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(DateTime timestamp) {
		this.timestamp = timestamp;
	}

	public double getOrderRatio() {
		return orderRatio;
	}

	public void setOrderRatio(double orderRatio) {
		this.orderRatio = orderRatio;
	}

	public double getWithdrawalAmount() {
		return withdrawalAmount;
	}

	public void setWithdrawalAmount(double withdrawalAmount) {
		this.withdrawalAmount = withdrawalAmount;
	}

	public String getOrderId() {
		return orderId;
	}

	public void setOrderId(String orderId) {
		this.orderId = orderId;
	}

	@Override
	public String toString() {
		return "CryptoOrder{" +
				"timestamp=" + timestamp +
				", orderRatio=" + orderRatio +
				", withdrawalAmount=" + withdrawalAmount +
				", orderId='" + orderId + '\'' +
				'}';
	}
}
