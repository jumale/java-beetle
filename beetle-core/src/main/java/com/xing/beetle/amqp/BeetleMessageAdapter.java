package com.xing.beetle.amqp;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Delivery;
import com.rabbitmq.client.LongString;
import com.xing.beetle.dedup.spi.MessageAdapter;
import com.xing.beetle.util.ExceptionSupport;

import java.io.IOException;

import static java.util.Objects.requireNonNull;

public class BeetleMessageAdapter implements MessageAdapter<Delivery> {

  private final Channel channel;
  private final boolean needToAck;
  private final boolean rejectAndRequeue;
  private static final int FLAG_REDUNDANT = 1;

  BeetleMessageAdapter(Channel channel, boolean needToAck, boolean rejectAndRequeue) {
    this.channel = requireNonNull(channel);
    this.needToAck = needToAck;
    this.rejectAndRequeue = rejectAndRequeue;
  }

  @Override
  public void drop(Delivery message) {
    if (needToAck) {
      try {
        channel.basicAck(message.getEnvelope().getDeliveryTag(), false);
      } catch (IOException e) {
        ExceptionSupport.sneakyThrow(e);
      }
    }
  }

  @Override
  public String keyOf(Delivery message) {
    return message.getProperties().getMessageId();
  }

  @Override
  public long expiresAt(Delivery message) {
    Object expiresAt = message.getProperties().getHeaders().get("expires_at");
    if (expiresAt == null) {
      return Long.MAX_VALUE;
    } else if (expiresAt instanceof Number) {
      return ((Number) expiresAt).longValue();
    } else if (expiresAt instanceof String) {
      return Long.parseLong((String) expiresAt);
    } else try {
      return Long.parseLong(expiresAt.toString());
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(
          "Unexpected expires_at header value " + expiresAt.getClass());
    }
  }

  @Override
  public boolean isRedundant(Delivery message) {
    Object flags = message.getProperties().getHeaders().get("flags");
    if (flags == null) {
      return false;
    } else if (flags instanceof Number) {
      return ((Number) flags).intValue() == FLAG_REDUNDANT;
    } else if (flags instanceof String) {
      return Integer.parseInt((String) flags) == FLAG_REDUNDANT;
    } else try {
      return Integer.parseInt(flags.toString()) == FLAG_REDUNDANT;
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Unexpected flags header value " + flags.getClass());
    }
  }

  @Override
  public void requeue(Delivery message) {
    if (needToAck) {
      try {
        channel.basicReject(message.getEnvelope().getDeliveryTag(), rejectAndRequeue);
      } catch (IOException e) {
        ExceptionSupport.sneakyThrow(e);
      }
    }
  }
}
