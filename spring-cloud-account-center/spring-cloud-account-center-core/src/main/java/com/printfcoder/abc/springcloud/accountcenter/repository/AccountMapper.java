package com.printfcoder.abc.springcloud.accountcenter.repository;

import com.printfcoder.abc.springcloud.accountcenter.account.domain.Account;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AccountMapper {

    @Select("SELECT\n"
        + "  id,\n"
        + "  account_name AS accountName,\n"
        + "  login_name   AS loginName,\n"
        + "  pwd\n"
        + "FROM account\n"
        + "WHERE login_name = #{loginName}")
    Account queryAccountByLoginName(@Param("loginName") String loginName);
}
