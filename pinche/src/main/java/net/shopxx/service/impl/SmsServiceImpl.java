/*
 * Copyright 2005-2017 shopxx.net. All rights reserved.
 * Support: http://www.shopxx.net
 * License: http://www.shopxx.net/license
 */
package net.shopxx.service.impl;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.sql.DataSource;

import com.google.common.collect.Maps;
import net.shopxx.entity.*;
import net.shopxx.util.DateCollect;
import net.shopxx.util.SnUtil;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.DateFormatUtils;
import org.json.simple.JSONObject;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.freemarker.FreeMarkerTemplateUtils;
import org.springframework.util.Assert;
import org.springframework.web.servlet.view.freemarker.FreeMarkerConfigurer;

import cn.emay.sdk.client.api.Client;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import net.shopxx.Setting;
import net.shopxx.TemplateConfig;
import net.shopxx.service.MessageConfigService;
import net.shopxx.service.SmsService;
import net.shopxx.util.SystemUtils;

/**
 * Service - 短信
 * 
 * @author SHOP++ Team
 * @version 5.0
 */
@Service
public class SmsServiceImpl implements SmsService {

	@Inject
	private FreeMarkerConfigurer freeMarkerConfigurer;
	@Inject
	private TaskExecutor taskExecutor;
	@Inject
	private MessageConfigService messageConfigService;

	/**
	 * 添加短信发送任务
	 * 
	 * @param mobiles
	 *            手机号码
	 * @param content
	 *            内容
	 * @param sendTime
	 *            发送时间
	 */
	private void addSendTask(final String[] mobiles, final String content, final Date sendTime) {
		taskExecutor.execute(new Runnable() {
			public void run() {
				send(mobiles, content, sendTime);
			}
		});
	}

	/**
	 * 发送短信
	 * 
	 * @param mobiles
	 *            手机号码
	 * @param content
	 *            内容
	 * @param sendTime
	 *            发送时间
	 */
	private void send(String[] mobiles, String content, Date sendTime) {
		Assert.notEmpty(mobiles);
		Assert.hasText(content);

		/*Setting setting = SystemUtils.getSetting();
		String smsSn = setting.getSmsSn();
		String smsKey = setting.getSmsKey();
		if (StringUtils.isEmpty(smsSn) || StringUtils.isEmpty(smsKey)) {
			return;
		}
		try {
			Client client = new Client(smsSn, smsKey);
			if (sendTime != null) {
				client.sendScheduledSMS(mobiles, content, DateFormatUtils.format(sendTime, "yyyyMMddhhmmss"));
			} else {
				client.sendSMS(mobiles, content, 5);
			}
		} catch (Exception e) {
			throw new RuntimeException(e.getMessage(), e);
		}*/

	}

	@Override
	public int checkSendSmsCountByMobile(String mobile) {
		return 0;
	}

	@Override
	public String sendValidateNo(String mobile) {
		int n = (int) (Math.floor(Math.random() * 1000000));
		StringBuffer sb = new StringBuffer(6);
		int length = 6 - String.valueOf(n).length();
		for (int i = 0; i < length; i++) {
			sb.append("0");
		}
		sb.append(n);
		send(mobile, sb.toString());
		return sb.toString();
	}

	public void send(String[] mobiles, String content, Date sendTime, boolean async) {
		Assert.notEmpty(mobiles);
		Assert.hasText(content);

		if (async) {
			addSendTask(mobiles, content, sendTime);
		} else {
			send(mobiles, content, sendTime);
		}
	}

	public void send(String[] mobiles, String templatePath, Map<String, Object> model, Date sendTime, boolean async) {
		Assert.notEmpty(mobiles);
		Assert.hasText(templatePath);

		try {
			Configuration configuration = freeMarkerConfigurer.getConfiguration();
			Template template = configuration.getTemplate(templatePath);
			String content = FreeMarkerTemplateUtils.processTemplateIntoString(template, model);
			send(mobiles, content, sendTime, async);
		} catch (TemplateException e) {
			throw new RuntimeException(e.getMessage(), e);
		} catch (IOException e) {
			throw new RuntimeException(e.getMessage(), e);
		}
	}


	/**
	 * 发送短信
	 * @param mobile
	 *            手机号码
	 * @param content
	 */
	public void send(final String mobile, final String content) {
		Assert.hasText(mobile);
		Assert.hasText(content);
//		send(new String[] { mobile }, content, null, true);
		final Date now = new Date();
		Setting setting = SystemUtils.getSetting();
		String channel = setting.getSmsChannel();
		final String sqlString = "insert into smscenter.t_send_q(mobile,content,sms_id,create_time,extend_param) values(?,?,?,?,?)";
		PreparedStatementCreator psc = new PreparedStatementCreator() {
			public PreparedStatement createPreparedStatement(Connection con) throws SQLException {
				PreparedStatement pstmt = con.prepareStatement(sqlString);
				int index = 1;
				if (org.springframework.util.StringUtils.hasText(mobile)) {
					pstmt.setString(index++, mobile);
				}
				if (org.springframework.util.StringUtils.hasText(content)) {
					pstmt.setString(index++, content);
				}
				pstmt.setInt(index++, Integer.parseInt(channel));
				pstmt.setDate(index++, new java.sql.Date(now.getTime()));
				pstmt.setString(index++, getSmsChannelInfo(channel));
				return pstmt;
			}
		};
	}

	private String getSmsChannelInfo(String channel){
		Map<String,Object> result = Maps.newConcurrentMap();
		result.put("signName","爱帮伴");
		result.put("effectiveTime","5");
		if(channel.equals("1") || channel.equals("3")){
			//众城通道
			result.put("smsTempleteContent","【${signName}】 您本次操作的短信验证码:${key1}，有效期：${effectiveTime}分钟，感谢您的支持！");
		}
		if(channel.equals("2")){
		//阿里通道
			result.put("tempId","SMS_136393689");
		}
		return JSONObject.toJSONString(result);
	}

	public void send(String mobile, String templatePath, Map<String, Object> model) {
		Assert.hasText(mobile);
		Assert.hasText(templatePath);

		send(new String[] { mobile }, templatePath, model, null, true);
	}

	public void sendRegisterMemberSms(Member member) {
		if (member == null || StringUtils.isEmpty(member.getMobile())) {
			return;
		}
		MessageConfig messageConfig = messageConfigService.find(MessageConfig.Type.registerMember);
		if (messageConfig == null || !messageConfig.getIsSmsEnabled()) {
			return;
		}
		Map<String, Object> model = new HashMap<>();
		model.put("member", member);
		TemplateConfig templateConfig = SystemUtils.getTemplateConfig("registerMemberSms");
		send(member.getMobile(), templateConfig.getTemplatePath(), model);
	}

	public void sendCreateOrderSms(Order order) {
		if (order == null || order.getMember() == null || StringUtils.isEmpty(order.getMember().getMobile())) {
			return;
		}
		MessageConfig messageConfig = messageConfigService.find(MessageConfig.Type.createOrder);
		if (messageConfig == null || !messageConfig.getIsSmsEnabled()) {
			return;
		}
		Map<String, Object> model = new HashMap<>();
		model.put("order", order);
		TemplateConfig templateConfig = SystemUtils.getTemplateConfig("createOrderSms");
		send(order.getMember().getMobile(), templateConfig.getTemplatePath(), model);
	}

	public void sendUpdateOrderSms(Order order) {
		if (order == null || order.getMember() == null || StringUtils.isEmpty(order.getMember().getMobile())) {
			return;
		}
		MessageConfig messageConfig = messageConfigService.find(MessageConfig.Type.updateOrder);
		if (messageConfig == null || !messageConfig.getIsSmsEnabled()) {
			return;
		}
		Map<String, Object> model = new HashMap<>();
		model.put("order", order);
		TemplateConfig templateConfig = SystemUtils.getTemplateConfig("updateOrderSms");
		send(order.getMember().getMobile(), templateConfig.getTemplatePath(), model);
	}

	public void sendCancelOrderSms(Order order) {
		if (order == null || order.getMember() == null || StringUtils.isEmpty(order.getMember().getMobile())) {
			return;
		}
		MessageConfig messageConfig = messageConfigService.find(MessageConfig.Type.cancelOrder);
		if (messageConfig == null || !messageConfig.getIsSmsEnabled()) {
			return;
		}
		Map<String, Object> model = new HashMap<>();
		model.put("order", order);
		TemplateConfig templateConfig = SystemUtils.getTemplateConfig("cancelOrderSms");
		send(order.getMember().getMobile(), templateConfig.getTemplatePath(), model);
	}

	public void sendReviewOrderSms(Order order) {
		if (order == null || order.getMember() == null || StringUtils.isEmpty(order.getMember().getMobile())) {
			return;
		}
		MessageConfig messageConfig = messageConfigService.find(MessageConfig.Type.reviewOrder);
		if (messageConfig == null || !messageConfig.getIsSmsEnabled()) {
			return;
		}
		Map<String, Object> model = new HashMap<>();
		model.put("order", order);
		TemplateConfig templateConfig = SystemUtils.getTemplateConfig("reviewOrderSms");
		send(order.getMember().getMobile(), templateConfig.getTemplatePath(), model);
	}

	public void sendPaymentOrderSms(Order order) {
		if (order == null || order.getMember() == null || StringUtils.isEmpty(order.getMember().getMobile())) {
			return;
		}
		MessageConfig messageConfig = messageConfigService.find(MessageConfig.Type.paymentOrder);
		if (messageConfig == null || !messageConfig.getIsSmsEnabled()) {
			return;
		}
		Map<String, Object> model = new HashMap<>();
		model.put("order", order);
		TemplateConfig templateConfig = SystemUtils.getTemplateConfig("paymentOrderSms");
		send(order.getMember().getMobile(), templateConfig.getTemplatePath(), model);
	}

	public void sendRefundsOrderSms(Order order) {
		if (order == null || order.getMember() == null || StringUtils.isEmpty(order.getMember().getMobile())) {
			return;
		}
		MessageConfig messageConfig = messageConfigService.find(MessageConfig.Type.refundsOrder);
		if (messageConfig == null || !messageConfig.getIsSmsEnabled()) {
			return;
		}
		Map<String, Object> model = new HashMap<>();
		model.put("order", order);
		TemplateConfig templateConfig = SystemUtils.getTemplateConfig("refundsOrderSms");
		send(order.getMember().getMobile(), templateConfig.getTemplatePath(), model);
	}

	public void sendShippingOrderSms(Order order) {
		if (order == null || order.getMember() == null || StringUtils.isEmpty(order.getMember().getMobile())) {
			return;
		}
		MessageConfig messageConfig = messageConfigService.find(MessageConfig.Type.shippingOrder);
		if (messageConfig == null || !messageConfig.getIsSmsEnabled()) {
			return;
		}
		Map<String, Object> model = new HashMap<>();
		model.put("order", order);
		TemplateConfig templateConfig = SystemUtils.getTemplateConfig("shippingOrderSms");
		send(order.getMember().getMobile(), templateConfig.getTemplatePath(), model);
	}

	public void sendReturnsOrderSms(Order order) {
		if (order == null || order.getMember() == null || StringUtils.isEmpty(order.getMember().getMobile())) {
			return;
		}
		MessageConfig messageConfig = messageConfigService.find(MessageConfig.Type.returnsOrder);
		if (messageConfig == null || !messageConfig.getIsSmsEnabled()) {
			return;
		}
		Map<String, Object> model = new HashMap<>();
		model.put("order", order);
		TemplateConfig templateConfig = SystemUtils.getTemplateConfig("returnsOrderSms");
		send(order.getMember().getMobile(), templateConfig.getTemplatePath(), model);
	}

	public void sendReceiveOrderSms(Order order) {
		if (order == null || order.getMember() == null || StringUtils.isEmpty(order.getMember().getMobile())) {
			return;
		}
		MessageConfig messageConfig = messageConfigService.find(MessageConfig.Type.receiveOrder);
		if (messageConfig == null || !messageConfig.getIsSmsEnabled()) {
			return;
		}
		Map<String, Object> model = new HashMap<>();
		model.put("order", order);
		TemplateConfig templateConfig = SystemUtils.getTemplateConfig("receiveOrderSms");
		send(order.getMember().getMobile(), templateConfig.getTemplatePath(), model);
	}

	public void sendCompleteOrderSms(Order order) {
		if (order == null || order.getMember() == null || StringUtils.isEmpty(order.getMember().getMobile())) {
			return;
		}
		MessageConfig messageConfig = messageConfigService.find(MessageConfig.Type.completeOrder);
		if (messageConfig == null || !messageConfig.getIsSmsEnabled()) {
			return;
		}
		Map<String, Object> model = new HashMap<>();
		model.put("order", order);
		TemplateConfig templateConfig = SystemUtils.getTemplateConfig("completeOrderSms");
		send(order.getMember().getMobile(), templateConfig.getTemplatePath(), model);
	}

	public void sendFailOrderSms(Order order) {
		if (order == null || order.getMember() == null || StringUtils.isEmpty(order.getMember().getMobile())) {
			return;
		}
		MessageConfig messageConfig = messageConfigService.find(MessageConfig.Type.failOrder);
		if (messageConfig == null || !messageConfig.getIsSmsEnabled()) {
			return;
		}
		Map<String, Object> model = new HashMap<>();
		model.put("order", order);
		TemplateConfig templateConfig = SystemUtils.getTemplateConfig("failOrderSms");
		send(order.getMember().getMobile(), templateConfig.getTemplatePath(), model);
	}

	public void sendRegisterBusinessSms(Business business) {
		if (business == null || StringUtils.isEmpty(business.getMobile())) {
			return;
		}
		MessageConfig messageConfig = messageConfigService.find(MessageConfig.Type.registerBusiness);
		if (messageConfig == null || !messageConfig.getIsSmsEnabled()) {
			return;
		}
		Map<String, Object> model = new HashMap<>();
		model.put("business", business);
		TemplateConfig templateConfig = SystemUtils.getTemplateConfig("registerBusinessSms");
		send(business.getMobile(), templateConfig.getTemplatePath(), model);

	}

	public void sendApprovalStoreSms(Store store) {
		if (store == null || StringUtils.isEmpty(store.getMobile())) {
			return;
		}
		MessageConfig messageConfig = messageConfigService.find(MessageConfig.Type.approvalStore);
		if (messageConfig == null || !messageConfig.getIsSmsEnabled()) {
			return;
		}
		Map<String, Object> model = new HashMap<>();
		model.put("store", store);
		TemplateConfig templateConfig = SystemUtils.getTemplateConfig("approvalStoreSms");
		send(store.getMobile(), templateConfig.getTemplatePath(), model);
	}

	public void sendFailStoreSms(Store store, String content) {
		if (store == null || StringUtils.isEmpty(store.getMobile()) || StringUtils.isEmpty(content)) {
			return;
		}
		MessageConfig messageConfig = messageConfigService.find(MessageConfig.Type.failStore);
		if (messageConfig == null || !messageConfig.getIsSmsEnabled()) {
			return;
		}
		Map<String, Object> model = new HashMap<>();
		model.put("store", store);
		model.put("content", content);
		TemplateConfig templateConfig = SystemUtils.getTemplateConfig("failStoreSms");
		send(store.getMobile(), templateConfig.getTemplatePath(), model);
	}

	public long getBalance() {
		Setting setting = SystemUtils.getSetting();
		String smsSn = setting.getSmsSn();
		String smsKey = setting.getSmsKey();
		if (StringUtils.isEmpty(smsSn) || StringUtils.isEmpty(smsKey)) {
			return -1L;
		}
		try {
			Client client = new Client(smsSn, smsKey);
			double balance = client.getBalance();
			if (balance >= 0) {
				return (long) Math.floor(balance / client.getEachFee());
			}
		} catch (Exception e) {
		}
		return -1L;
	}

}