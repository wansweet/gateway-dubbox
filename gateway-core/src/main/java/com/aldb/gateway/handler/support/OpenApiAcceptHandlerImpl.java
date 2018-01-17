/**
 * 
 */
package com.aldb.gateway.handler.support;

import com.aldb.gateway.common.OpenApiHttpRequestBean;
import com.aldb.gateway.common.util.CommonCodeConstants;
import com.aldb.gateway.handler.OpenApiAcceptHandler;
import com.aldb.gateway.handler.OpenApiHandlerExecuteTemplate;
import com.aldb.gateway.handler.ThreadPoolHandler;
import com.aldb.gateway.protocol.OpenApiHttpReqTask;
import com.aldb.gateway.protocol.OpenApiHttpSessionBean;
import com.aldb.gateway.service.IdService;
import com.aldb.gateway.util.OpenApiResponseUtils;
import com.alibaba.fastjson.JSON;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author Administrator
 * 
 */

public class OpenApiAcceptHandlerImpl implements OpenApiAcceptHandler, ApplicationContextAware {

    private static Log logger = LogFactory.getLog(OpenApiAcceptHandlerImpl.class);
    private IdService idService;

    private ThreadPoolHandler poolHandler;
    
    
    public void setIdService(IdService idService) {
        this.idService = idService;
    }

   

    public void setPoolHandler(ThreadPoolHandler poolHandler) {
        this.poolHandler = poolHandler;
    }

    @Override
    public void acceptRequest(HttpServletRequest request, HttpServletResponse response) {
        OpenApiHttpRequestBean reqBean = (OpenApiHttpRequestBean) request
                .getAttribute(CommonCodeConstants.REQ_BEAN_KEY);
        String traceId = idService.genInnerRequestId();
        reqBean.setTraceId(traceId);
        request.setAttribute(CommonCodeConstants.REQ_BEAN_KEY, reqBean); // 重新设置bean
        if (logger.isInfoEnabled()) {
            logger.info(String.format("requestId=%s request begin,reqeust=%s", traceId, JSON.toJSONString(reqBean)));
        }
        // 将当前请求放入线程池处理，若超过线程池最大处理数则抛出reach queue max deepth 异常
        addTask2Pool(response, new OpenApiHttpSessionBean(reqBean));
    }

    

    private void addTask2Pool(HttpServletResponse response, OpenApiHttpSessionBean sessionBean) {
        long currentTime = System.currentTimeMillis();
        if (logger.isDebugEnabled()) {
            logger.debug(String.format("begin deal_sessionbean,current_time=%d,sessionbean=%s ", currentTime,
                    sessionBean));
        }
        logger.info("added one task to thread pool");
        OpenApiHttpReqTask task = null;
        String operationType = sessionBean.getRequest().getOperationType();

        OpenApiHandlerExecuteTemplate handlerExecuteTemplate = applicationContext.getBean(operationType,
                OpenApiHandlerExecuteTemplate.class);
        task = new OpenApiHttpReqTask(sessionBean, handlerExecuteTemplate);
        /**
         * 走责任链，将相关的请求处理
         */
        OpenApiHttpSessionBean tmp = (OpenApiHttpSessionBean) poolHandler.addTask(task);
        // 写入响应
        OpenApiResponseUtils.writeRsp(response, tmp.getRequest());
        if (logger.isDebugEnabled()) {
            logger.debug(String.format(
                    "end deal_sessionbean,current_time=%d,elapase_time=%d milseconds,sessionbean=%s",
                    System.currentTimeMillis(), (System.currentTimeMillis() - currentTime), tmp));
        }
    }

    private ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

}
