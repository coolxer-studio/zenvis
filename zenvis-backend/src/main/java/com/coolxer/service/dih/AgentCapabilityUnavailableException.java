package com.coolxer.service.dih;

/**
 * 智能体声明的 Skill 或其他必需能力不可用。
 */
public class AgentCapabilityUnavailableException extends RuntimeException {

    public AgentCapabilityUnavailableException(String message) {
        super(message);
    }

    public AgentCapabilityUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
