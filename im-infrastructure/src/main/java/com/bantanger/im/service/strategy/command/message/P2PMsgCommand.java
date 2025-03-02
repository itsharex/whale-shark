package com.bantanger.im.service.strategy.command.message;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.bantanger.im.codec.proto.ChatMessageAck;
import com.bantanger.im.codec.proto.Message;
import com.bantanger.im.codec.proto.MessagePack;
import com.bantanger.im.common.ResponseVO;
import com.bantanger.im.common.enums.command.MessageCommand;
import com.bantanger.im.common.model.message.CheckSendMessageReq;
import com.bantanger.im.service.feign.FeignMessageService;
import com.bantanger.im.service.rabbitmq.publish.MqMessageProducer;
import com.bantanger.im.service.strategy.command.BaseCommandStrategy;
import com.bantanger.im.service.strategy.command.model.CommandExecutionRequest;
import io.netty.channel.ChannelHandlerContext;

/**
 * TCP 层校验消息发送方合法性
 * @author BanTanger 半糖
 * @Date 2023/4/5 20:06
 */
public class P2PMsgCommand extends BaseCommandStrategy {

    @Override
    public void systemStrategy(CommandExecutionRequest commandExecutionRequest) {
        ChannelHandlerContext ctx = commandExecutionRequest.getCtx();
        Message msg = commandExecutionRequest.getMsg();
        FeignMessageService feignMessageService = commandExecutionRequest.getFeignMessageService();

        CheckSendMessageReq req = new CheckSendMessageReq();
        req.setAppId(msg.getMessageHeader().getAppId());
        req.setCommand(msg.getMessageHeader().getCommand());
        JSONObject jsonObject = JSON.parseObject(JSONObject.toJSONString(msg.getMessagePack()));
        String fromId = jsonObject.getString("fromId");
        String toId = jsonObject.getString("toId");
        req.setFromId(fromId);
        req.setToId(toId);

        // 1.调用业务层校验消息发送方的内部接口
        ResponseVO responseVO = feignMessageService.checkP2PSendMessage(req);
        if (responseVO.isOk()) {
            // 2. 如果成功就投递到 MQ
            MqMessageProducer.sendMessage(msg, req.getCommand());
        } else {
            // 3. 如果失败就发送 ACK 失败响应报文
            ChatMessageAck chatMessageAck = new ChatMessageAck(jsonObject.getString("messageId"));
            responseVO.setData(chatMessageAck);
            MessagePack<ResponseVO> ack = new MessagePack<>();
            ack.setData(responseVO);
            ack.setCommand(MessageCommand.MSG_ACK.getCommand());
            ctx.channel().writeAndFlush(ack);
        }

    }

}