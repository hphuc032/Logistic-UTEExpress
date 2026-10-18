package com.uteexpress.fulfillment.service;

import com.uteexpress.common.exception.*;
import com.uteexpress.fulfillment.dto.*;
import com.uteexpress.order.service.OrderLifecycleService;
import com.uteexpress.shipping.dto.DeliveryAttemptOperation;
import com.uteexpress.shipping.service.ShipmentAttemptService;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.beans.factory.ObjectProvider;

/** Fresh Order-first transaction; TD prepare/verify remains the mandatory lifecycle authority. */
@Service
@PreAuthorize("hasAuthority(T(com.uteexpress.security.RoleCode).SHIPPER.authority())")
public class DeliveryAttemptCoordinator {
    private final OrderLifecycleService orders;
    private final ShipmentAttemptService attempts;
    private final ObjectProvider<PlatformTransactionManager> managers;
    private final Validator validator;
    public DeliveryAttemptCoordinator(OrderLifecycleService orders, ShipmentAttemptService attempts,
            ObjectProvider<PlatformTransactionManager> managers, Validator validator) {
        this.orders=orders; this.attempts=attempts; this.managers=managers; this.validator=validator;
    }
    public DeliveryAttemptResult reportFailure(Long orderId, DeliveryFailureRequest request) {
        validate(request);
        return execute(orderId,request.expectedOrderVersion(),request.expectedShipmentVersion(),
                DeliveryAttemptOperation.REPORT_DELIVERY_FAILURE,
                () -> attempts.reportFailure(orderId,request.expectedShipmentVersion(),request.failureReason(),request.evidenceReference()));
    }
    public DeliveryAttemptResult retryDelivery(Long orderId, RetryDeliveryRequest request) {
        validate(request);
        return execute(orderId,request.expectedOrderVersion(),request.expectedShipmentVersion(),
                DeliveryAttemptOperation.RETRY_DELIVERY,() -> attempts.retryDelivery(orderId,request.expectedShipmentVersion()));
    }
    private DeliveryAttemptResult execute(Long orderId, Long ov, Long sv, DeliveryAttemptOperation operation, Runnable mutation) {
        if (orderId==null || orderId<=0 || ov==Long.MAX_VALUE || sv==Long.MAX_VALUE)
            throw new ApplicationException(ErrorCode.INVALID_REQUEST);
        if (TransactionSynchronizationManager.isActualTransactionActive())
            throw new ApplicationException(ErrorCode.INVALID_REQUEST);
        return new TransactionTemplate(managers.getObject()).execute(tx -> {
            orders.prepareShipmentOnlyOperation(orderId,ov,sv,operation);
            mutation.run();
            orders.verifyShipmentOnlyOperation(orderId,ov,sv,operation);
            var facts=attempts.requireOperationEvidence(orderId,sv,operation).shipment();
            // The trusted Order guard has verified this original Order token remains unchanged.
            return new DeliveryAttemptResult(orderId,"SHIPPING",ov,facts.status(),facts.version(),facts.attemptCount());
        });
    }
    private void validate(Object request) {
        if (request==null || !validator.validate(request).isEmpty())
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED);
    }
}
