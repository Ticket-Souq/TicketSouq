package org.ticketsouq.paymentservice.paymentProviders;

import org.ticketsouq.paymentservice.dto.PayoutResult;
import org.ticketsouq.paymentservice.model.Payout;

public interface PayoutProvider {

    PayoutResult payout(Payout payout);
}
