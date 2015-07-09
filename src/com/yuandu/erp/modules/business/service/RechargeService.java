package com.yuandu.erp.modules.business.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.yuandu.erp.common.persistence.Page;
import com.yuandu.erp.common.service.CrudService;
import com.yuandu.erp.modules.business.dao.RechargeDao;
import com.yuandu.erp.modules.business.entity.PartnerOrder;
import com.yuandu.erp.modules.business.entity.Recharge;
import com.yuandu.erp.modules.sys.dao.UserDao;
import com.yuandu.erp.modules.sys.entity.User;
import com.yuandu.erp.modules.sys.utils.UserUtils;
import com.yuandu.erp.webservice.utils.BusinessUtil;
import com.yuandu.erp.webservice.utils.DefaultResponse;
import com.yuandu.erp.webservice.utils.ProductCacheUtil;

/**
 * 充值Service
 */
@Service
@Transactional(readOnly = true)
public class RechargeService extends CrudService<RechargeDao, Recharge> {

	@Autowired
	private UserDao userDao;
	@Autowired
	private PartnerOrderService partnerOrderService;

	public Recharge get(String id) {
		Recharge entity = dao.get(id);
		return entity;
	}
	
	public Page<Recharge> find(Page<Recharge> page, Recharge recharge) {
		recharge.getSqlMap().put("dsf", dataScopeFilter(UserUtils.getUser(), "o", "u"));
		recharge.setPage(page);
		page.setList(dao.findList(recharge));
		return page;
	}
	
	@Transactional(readOnly = false)
	public DefaultResponse saveRecharge(Recharge recharge) throws Exception {
		
		recharge.preInsert();
		// 先调用充值接口
		DefaultResponse response = BusinessUtil.buyFlow(recharge);
		String orderNo = response.getData().get("orderNo");
		recharge.setOrderNo(orderNo);
		recharge.setNotifyUrl(Recharge.notify_url);
		
		User user = UserUtils.getUser();
		if(recharge.getCreateBy()!=null&&recharge.getCreateBy().getId()!=null){
			user = recharge.getCreateBy();
		}
		// 调用查询接口
		PartnerOrder order = ProductCacheUtil.getPartnerOrder(user,recharge.getPartnerOrderNo());
		// 保存order方便查询
		order.setId(null);
		partnerOrderService.save(order);
		String status = order.getStatus();
		
		recharge.setStatus(status);
		recharge.setType(order.getFlowType());
		recharge.setFlowSize(order.getFlowSize());
		//设置balance
		Double balance = order.getBalance();
		
		recharge.setBalance(balance);
		dao.insert(recharge);
		//更新用户余额
		UserUtils.updateBalance(UserUtils.getUser(),recharge.getBalance(), status);
		
		return response;
	}
	
	/**
	 * 产生订单号
	 * @return
	 */
	@Transactional(readOnly = false)
	public String createOrder(){
		return dao.createOrder();
	}

	/**
	 * 更新账单状态
	 * @param partnerOrderNo
	 * @param status
	 * @throws Exception 
	 */
	@Transactional(readOnly = false)
	public void updateStatus(User user,String partnerOrderNo, String status) throws Exception {
		
		//判断状态是否为1
		PartnerOrder order = ProductCacheUtil.getPartnerOrder(user, partnerOrderNo);
		if(order!=null&&"1".equals(order.getStatus())){//已经扣款
			throw new RuntimeException("订单["+partnerOrderNo+"] 已经扣费，无法更新状态");
		}
		
		Recharge entity = new Recharge();
		entity.setPartnerOrderNo(partnerOrderNo);
		entity.setStatus(status);
		entity.setUpdateBy(user);
		entity.preUpdate();
		
		dao.updateStatus(entity);
	}
}