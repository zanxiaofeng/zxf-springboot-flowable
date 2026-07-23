package zxf.flowable.saga.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zxf.flowable.saga.dao.OrderDao;

@RequiredArgsConstructor
@Service
public class OrderService {
    private final OrderDao orderDao;

    @Transactional(rollbackFor = Throwable.class, value = "businessTransactionManager")
    public boolean createOrder(String orderId) {
        return orderDao.createOrder(orderId);
    }
}
