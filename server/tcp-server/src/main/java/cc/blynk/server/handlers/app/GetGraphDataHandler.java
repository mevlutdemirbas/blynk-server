package cc.blynk.server.handlers.app;

import cc.blynk.common.model.messages.protocol.appllication.GetGraphDataMessage;
import cc.blynk.common.model.messages.protocol.appllication.GetGraphDataResponseMessage;
import cc.blynk.common.utils.ServerProperties;
import cc.blynk.common.utils.StringUtils;
import cc.blynk.server.dao.SessionsHolder;
import cc.blynk.server.dao.UserRegistry;
import cc.blynk.server.dao.graph.GraphKey;
import cc.blynk.server.dao.graph.Storage;
import cc.blynk.server.exceptions.IllegalCommandException;
import cc.blynk.server.exceptions.ServerException;
import cc.blynk.server.handlers.BaseSimpleChannelInboundHandler;
import cc.blynk.server.model.auth.User;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.Queue;
import java.util.zip.DeflaterOutputStream;

/**
 * The Blynk Project.
 * Created by Dmitriy Dumanskiy.
 * Created on 2/1/2015.
 *
 */
@ChannelHandler.Sharable
public class GetGraphDataHandler extends BaseSimpleChannelInboundHandler<GetGraphDataMessage> {

    private final static byte[] EMPTY_RESPONSE = {};
    private final Storage storage;

    public GetGraphDataHandler(ServerProperties props, UserRegistry userRegistry, SessionsHolder sessionsHolder, Storage storage) {
        super(props, userRegistry, sessionsHolder);
        this.storage = storage;
    }

    public static byte[] compress(Queue<String> values, int msgId) {
        //todo define size
        ByteArrayOutputStream baos = new ByteArrayOutputStream(values.size() * 20);

        try {
            OutputStream out = new DeflaterOutputStream(baos);
            int counter = 0;
            int length = values.size();
            for (String s : values) {
                counter++;
                out.write(s.getBytes());
                if (counter < length) {
                    out.write(StringUtils.BODY_SEPARATOR);
                }
            }
            out.close();
        } catch (Exception ioe) {
            throw new ServerException(msgId);
        }
        return baos.toByteArray();
    }

    @Override
    protected void messageReceived(ChannelHandlerContext ctx, User user, GetGraphDataMessage message) {
        //warn: split may be optimized
        String[] messageParts = message.body.split(" ", 2);

        if (messageParts.length != 2) {
            throw new IllegalCommandException("Wrong income message format.", message.id);
        }

        String dashBoardIdString = messageParts[0];
        String pinString = messageParts[1];

        int dashBoardId;
        byte pin;

        try {
            dashBoardId = Integer.parseInt(dashBoardIdString);
            pin = Byte.parseByte(pinString);
        } catch (NumberFormatException e) {
            throw new IllegalCommandException("Hardware command body incorrect.", message.id);
        }

        user.getProfile().validateDashId(dashBoardId, message.id);

        Queue<String> allValues = storage.getAll(new GraphKey(dashBoardId, pin));
        GetGraphDataResponseMessage response;
        if (allValues == null || allValues.size() == 0) {
            response = new GetGraphDataResponseMessage(message.id, EMPTY_RESPONSE);
        } else {
            byte[] compressed = compress(allValues, message.id);
            response = new GetGraphDataResponseMessage(message.id, compressed);
        }
        ctx.writeAndFlush(response);
    }

}
