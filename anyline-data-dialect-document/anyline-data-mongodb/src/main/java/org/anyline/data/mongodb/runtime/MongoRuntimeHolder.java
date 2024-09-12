/*
 * Copyright 2006-2023 www.anyline.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.anyline.data.mongodb.runtime;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import org.anyline.annotation.Component;
import org.anyline.bean.BeanDefine;
import org.anyline.bean.init.DefaultBeanDefine;
import org.anyline.bean.init.DefaultValueReference;
import org.anyline.dao.init.DefaultDao;
import org.anyline.data.adapter.DriverAdapter;
import org.anyline.data.adapter.DriverAdapterHolder;
import org.anyline.data.datasource.DataSourceMonitor;
import org.anyline.data.mongodb.adapter.MongoAdapter;
import org.anyline.data.runtime.DataRuntime;
import org.anyline.data.runtime.RuntimeHolder;
import org.anyline.data.runtime.init.AbstractRuntimeHolder;
import org.anyline.service.init.DefaultService;
import org.anyline.util.ConfigTable;

import javax.sql.DataSource;
import java.util.Map;

@Component("anyline.environment.data.runtime.holder.mongo")
public class MongoRuntimeHolder extends AbstractRuntimeHolder implements RuntimeHolder {
    private static final MongoRuntimeHolder instance = new MongoRuntimeHolder();
    public static MongoRuntimeHolder instance() {
        return instance;
    }
    public MongoRuntimeHolder() {
    }

    /**
     * 注册数据源 子类覆盖 生成简单的DataRuntime不注册到
     * @param datasource 数据源, MongoDatabase, MongoClient
     * @param database 数据库, Mongo类型数据源不需要
     * @param adapter 如果确认数据库类型可以提供如 new MySQLAdapter(), 如果不提供则根据ds检测
     * @return DataRuntime
     * @throws Exception 异常 Exception
     */
    public DataRuntime temporary(Object datasource, String database, DriverAdapter adapter) throws Exception {
        MongoRuntime runtime = new MongoRuntime();
        if(datasource instanceof MongoDatabase || datasource instanceof MongoClient) {
            String key = "temporary_mongo";
            //关闭上一个
            close(key);
            temporary.remove(key);
            runtimes.remove(key);
            //DriverAdapterHolder.remove(key);
            //创建新数据源
            runtime.setKey(key);
            runtime.setAdapter(adapter);
            runtime.setProcessor(datasource);
            temporary.put(key, datasource);
            log.warn("[创建临时数据源][key:{}][type:{}]", key, datasource.getClass().getSimpleName());
            runtimes.put(key, runtime);
        }else{
            throw new Exception("请提供 com.mongodb.client.MongoDatabase 或 com.mongodb.client.MongoClient 兼容类型");
        }
        //runtime.setHolder(this);
        return runtime;
    }

    /**
     * 注册运行环境
     * @param key 数据源前缀
     * @param datasource 数据源bean id
     */
    public DataRuntime reg(String key, String datasource) {
        return null;
    }

    public DataRuntime reg(String key, MongoClient client, MongoDatabase database) {
        String datasource_key = DataRuntime.ANYLINE_DATASOURCE_BEAN_PREFIX + key;
        log.info("[注入数据源][type:mongo][key:{}][bean:{}]", key, datasource_key);
        ConfigTable.environment().regBean(datasource_key, database);
        return reg(key, client, database, null);
    }

    /**
     * 注册运行环境
     * @param datasource 数据源前缀
     * @param database MongoDatabase
     * @param adapter adapter 可以为空 第一次执行时补齐
     */
    public MongoRuntime reg(String datasource,  MongoClient client, MongoDatabase database, MongoAdapter adapter) {
        log.debug("[create Mongo runtime][key:{}]", datasource);
        MongoRuntime runtime = new MongoRuntime(datasource, client, database, adapter);
        if(runtimes.containsKey(datasource)) {
            destroy(datasource);
        }
        runtimes.put(datasource, runtime);

        String dao_key = DataRuntime.ANYLINE_DAO_BEAN_PREFIX +  datasource;
        String service_key = DataRuntime.ANYLINE_SERVICE_BEAN_PREFIX +  datasource;
        log.debug("[instance service][data source:{}][instance id:{}]", datasource, service_key);

        BeanDefine daoDefine = new DefaultBeanDefine(DefaultDao.class);
        daoDefine.addValue("runtime", runtime);
        daoDefine.setLazy(true);
        ConfigTable.environment().regBean(dao_key, daoDefine);

        BeanDefine serviceDefine = new DefaultBeanDefine(DefaultService.class);
        serviceDefine.addValue("dao", new DefaultValueReference(dao_key));
        serviceDefine.setLazy(true);
        ConfigTable.environment().regBean(service_key, serviceDefine);
        return runtime;
    }
    public boolean destroy(String key) {

        int close = 0;
        DataSourceMonitor monitor = DriverAdapterHolder.getMonitor();
        if(null != monitor){
            MongoRuntime runtime = (MongoRuntime) runtimes.get(key);
            if(null != runtime){
                //这一步有可能抛出 异常
                close = monitor.destroy(runtime, key, runtime.client());
            }
        }
        try {
            runtimes.remove(key);
            ConfigTable.environment().destroyBean(DataRuntime.ANYLINE_SERVICE_BEAN_PREFIX +  key);
            ConfigTable.environment().destroyBean(DataRuntime.ANYLINE_DAO_BEAN_PREFIX +  key);
            ConfigTable.environment().destroyBean(DataRuntime.ANYLINE_TRANSACTION_BEAN_PREFIX +  key);
            if(close == 0) {
                close(DataRuntime.ANYLINE_DATASOURCE_BEAN_PREFIX + key);
            }
            ConfigTable.environment().destroyBean(DataRuntime.ANYLINE_DATASOURCE_BEAN_PREFIX + key);
            log.warn("[注销数据源及相关资源][key:{}]", key);
            //从当前数据源复制的 子源一块注销
            Map<String, DataRuntime> runtimes = RuntimeHolder.runtimes(key);
            for(String item:runtimes.keySet()) {
                destroy(item);
            }
        }catch (Exception e) {
            log.error("注销数据源 异常:", e);
            return false;
        }
        return true;
    }

    public static void close(String key) {
        /*Object datasource = null;
        if(ConfigTable.environment().containsBean(key)) {
            datasource = ConfigTable.environment().getBean(key);
            try {
                closeConnection(datasource);
            }catch (Exception e) {
                log.error("close connection exception:", e);
            }
        }
        //临时数据源
        if(temporary.containsKey(key)) {
            try {
                closeConnection(temporary.get(key));
            } catch (Exception e) {
                log.error("close connection exception:", e);
            }
        }*/
    }
    public static void closeConnection(Object datasource) throws Exception {
        /*Method method = ClassUtil.getMethod(datasource.getClass(), "close");
        if(null != method) {
            method.invoke(datasource);
        }*/
    }
}