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

package org.anyline.data.prepare.auto.init;

import org.anyline.data.prepare.RunPrepare;
import org.anyline.data.prepare.auto.TextPrepare;
import org.anyline.data.run.Run;
import org.anyline.data.run.TextRun;
import org.anyline.data.runtime.DataRuntime;
import org.anyline.entity.DataRow;
import org.anyline.entity.GroupStore;
import org.anyline.entity.OriginRow;
import org.anyline.util.BasicUtil;

public class DefaultTextPrepare extends DefaultAutoPrepare implements TextPrepare {
	private String up;
	private String order;
	private String having;
	private String group;
	private String where;
	public DefaultTextPrepare(String text) {
		super(); 
		this.text = text;
		chain = new DefaultAutoConditionChain();
		split();
	}
	public String getText() {
		return this.text; 
	}

	@Override
	public Run build(DataRuntime runtime) {
		TextRun run = new TextRun();
		run.setPrepare(this);
		run.setRuntime(runtime);
		if(null != order){
			run.setOrders(orders);
		}
		if(null != group){
			GroupStore groups = run.getGroups();
			groups.add(group);
		}
		if(null != having){
			run.having(having);
		}
		if(null != where){
			run.addCondition(where);
		}
		return run;
	}

	@Override
	public DataRow map(boolean empty, boolean join) {
		DataRow row = new OriginRow();
		row.put("text", text);
		return row;
	}

	@Override
	public RunPrepare setContent(String content) {
		this.text = content;
		return this;
	}

	private void split(){
		text = text.replaceAll("\\s{2,}", " ");
		up = text.toUpperCase();
		order = split("ORDER BY");
		having = split("HAVING ");
		group = split("GROUP BY");
		where = split("WHERE ");
	}
	private String split(String type){
		int idx = up.lastIndexOf(type);
		if(idx != -1) {
			String chk = up.substring(idx);
			if (BasicUtil.charCount(chk, "(") == BasicUtil.charCount(chk, ")")) {
				if (BasicUtil.charCount(chk, "'")%2 == 0) {
					up = up.substring(0, idx);
					String result = text.substring(idx + type.length());
					text = text.substring(0, idx);
					return result;
				}
			}
		}
		return null;
	}
}
