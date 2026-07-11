package org.ticketsouq.sharedmodule.ApiGateway.dto;

public record GenerateAccountRequest(String orgId,int agentCount, int consumerCount) {
}
