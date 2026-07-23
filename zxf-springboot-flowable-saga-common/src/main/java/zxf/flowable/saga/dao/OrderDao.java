package zxf.flowable.saga.dao;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class OrderDao {
    private final NamedParameterJdbcTemplate restTemplate;

    public boolean createOrder(String orderId) {
        String sql = "INSERT INTO TBL_ORDER(ORDER_ID,CREATED_AT) value(:orderId, NOW())";
        MapSqlParameterSource parameterSource = new MapSqlParameterSource();
        parameterSource.addValue("orderId", orderId);
        return restTemplate.update(sql, parameterSource) > 0;
    }
}
