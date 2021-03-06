package io.github.pleuvoir.rabbit.reliable.template;

import io.github.pleuvoir.rabbit.reliable.MessageCommitLog;
import io.github.pleuvoir.rabbit.reliable.MessageLogRepository;
import io.github.pleuvoir.rabbit.utils.Generator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.LocalDateTime;

public class ReliableRabbitPublishTemplate extends RabbitTemplate {

	private final static Logger LOGGER = LoggerFactory.getLogger(ReliableRabbitPublishTemplate.class);

	@Resource(name = "jdbcMessageLogRepository")
	private MessageLogRepository repository;

	private PublishTemplateConfig templateConfig;
	
	public void setTemplateConfig(PublishTemplateConfig templateConfig) {
		this.templateConfig = templateConfig;
	}

	public ReliableRabbitPublishTemplate(ConnectionFactory connectionFactory) {
		super(connectionFactory);
	}

	@PostConstruct
	void setup() {
		super.setBeforePublishPostProcessors(new MessagePostProcessor() {
			@Override
			public Message postProcessMessage(Message message) throws AmqpException {
				MessageProperties messageProperties = message.getMessageProperties();
				String messageId = Generator.nextUUID();
				messageProperties.setMessageId(messageId);

				MessageCommitLog log = MessageCommitLog.builder().
						createTime(LocalDateTime.now()).
						id(messageId).
						body(new String(message.getBody())).
						status(MessageCommitLog.PREPARE_TO_BROKER).
                        retryCount(0).
						maxRetry(templateConfig.getMaxRetry()).
						build();

                repository.insert(log);

                LOGGER.info("*[messageId={}] 准备发送消息到 MQ Broker", messageId);

				return message;
			}
		});
	}

}
