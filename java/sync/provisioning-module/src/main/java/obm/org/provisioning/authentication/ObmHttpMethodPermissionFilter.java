/* ***** BEGIN LICENSE BLOCK *****
 *
 * Copyright (C) 2011-2013  Linagora
 *
 * This program is free software: you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version, provided you comply
 * with the Additional Terms applicable for OBM connector by Linagora
 * pursuant to Section 7 of the GNU Affero General Public License,
 * subsections (b), (c), and (e), pursuant to which you must notably (i) retain
 * the “Message sent thanks to OBM, Free Communication by Linagora”
 * signature notice appended to any and all outbound messages
 * (notably e-mail and meeting requests), (ii) retain all hypertext links between
 * OBM and obm.org, as well as between Linagora and linagora.com, and (iii) refrain
 * from infringing Linagora intellectual property rights over its trademarks
 * and commercial brands. Other Additional Terms apply,
 * see <http://www.linagora.com/licenses/> for more details.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * and its applicable Additional Terms for OBM along with this program. If not,
 * see <http://www.gnu.org/licenses/> for the GNU Affero General Public License version 3
 * and <http://www.linagora.com/licenses/> for the Additional Terms applicable to
 * OBM connectors.
 *
 * ***** END LICENSE BLOCK ***** */
package obm.org.provisioning.authentication;

import java.util.HashMap;
import java.util.Map;

import org.apache.shiro.web.filter.authz.HttpMethodPermissionFilter;

public class ObmHttpMethodPermissionFilter extends HttpMethodPermissionFilter {
	
    private static final String CREATE_ACTION = "create";
    private static final String READ_ACTION = "read";
    private static final String UPDATE_ACTION = "update";
    private static final String DELETE_ACTION = "delete";
    private static final String PATCH_ACTION = "patch";
	
	private final Map<String, String> httpMethodActions = new HashMap<String, String>();
	
    private static enum HttpMethodAction {

        DELETE(DELETE_ACTION),
        GET(READ_ACTION),
        POST(CREATE_ACTION),
        PUT(UPDATE_ACTION),
        PATCH(PATCH_ACTION);

        private final String action;

        private HttpMethodAction(String action) {
            this.action = action;
        }

        public String getAction() {
            return this.action;
        }
    }
	
    public ObmHttpMethodPermissionFilter() {
        for (HttpMethodAction methodAction : HttpMethodAction.values()) {
            httpMethodActions.put(methodAction.name().toLowerCase(), methodAction.getAction());
        }
    }
    
    @Override
    protected Map<String, String> getHttpMethodActions() {
        return this.httpMethodActions;
    }
}
