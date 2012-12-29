/*
 * Copyright (C) 2011 Thomas Akehurst
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.tomakehurst.wiremock.stubbing;

import com.github.tomakehurst.wiremock.global.RequestDelayControl;
import com.github.tomakehurst.wiremock.client.RequestDelaySpec;
import com.github.tomakehurst.wiremock.common.Json;
import com.github.tomakehurst.wiremock.global.GlobalSettings;
import com.github.tomakehurst.wiremock.global.GlobalSettingsHolder;
import com.github.tomakehurst.wiremock.http.HttpHeaders;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import com.github.tomakehurst.wiremock.servlet.ResponseRenderer;
import com.github.tomakehurst.wiremock.verification.FindRequestsResult;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.github.tomakehurst.wiremock.verification.RequestJournal;
import com.github.tomakehurst.wiremock.verification.VerificationResult;

import java.util.List;

import static com.github.tomakehurst.wiremock.WireMockApp.ADMIN_CONTEXT_ROOT;
import static com.github.tomakehurst.wiremock.common.LocalNotifier.notifier;
import static com.github.tomakehurst.wiremock.http.HttpHeader.httpHeader;
import static com.github.tomakehurst.wiremock.common.Json.buildRequestPatternFrom;
import static com.github.tomakehurst.wiremock.common.Json.write;
import static java.net.HttpURLConnection.HTTP_OK;

public class AdminRequestHandler extends AbstractRequestHandler {
    
    private final StubMappings stubMappings;
	private final JsonStubMappingCreator jsonStubMappingCreator;
	private final RequestJournal requestJournal;
	private final GlobalSettingsHolder globalSettingsHolder;
    private final RequestDelayControl requestDelayControl;
	
	public AdminRequestHandler(StubMappings stubMappings,
                               RequestJournal requestJournal,
                               GlobalSettingsHolder globalSettingsHolder,
                               ResponseRenderer responseRenderer,
                               RequestDelayControl requestDelayControl) {
		super(responseRenderer);
		this.stubMappings = stubMappings;
		this.requestJournal = requestJournal;
		this.globalSettingsHolder = globalSettingsHolder;
        this.requestDelayControl = requestDelayControl;
        jsonStubMappingCreator = new JsonStubMappingCreator(stubMappings);
	}

	@Override
	public ResponseDefinition handleRequest(Request request) {
        notifier().info("Received request to " + request.getUrl() + " with body " + request.getBodyAsString());

		if (isNewMappingRequest(request)) {
			jsonStubMappingCreator.addMappingFrom(request.getBodyAsString());
			return ResponseDefinition.created();
		} else if (isResetRequest(request)) {
			stubMappings.reset();
			requestJournal.reset();
            requestDelayControl.clearDelay();
			return ResponseDefinition.ok();
		} else if (isResetScenariosRequest(request)) {
			stubMappings.resetScenarios();
			return ResponseDefinition.ok();
		} else if (isRequestCountRequest(request)) {
			return getRequestCount(request);
        } else if (isFindRequestsRequest(request)) {
            return findRequests(request);
		} else if (isGlobalSettingsUpdateRequest(request)) {
			GlobalSettings newSettings = Json.read(request.getBodyAsString(), GlobalSettings.class);
			globalSettingsHolder.replaceWith(newSettings);
			return ResponseDefinition.ok();
        } else if (isSocketDelayRequest(request)) {
            RequestDelaySpec delaySpec = Json.read(request.getBodyAsString(), RequestDelaySpec.class);
            requestDelayControl.setDelay(delaySpec.milliseconds());
            return ResponseDefinition.ok();
		} else {
			return ResponseDefinition.notFound();
		}
	}

	private boolean isGlobalSettingsUpdateRequest(Request request) {
		return request.getMethod() == RequestMethod.POST && withoutAdminRoot(request.getUrl()).equals("/settings");
	}

	private ResponseDefinition getRequestCount(Request request) {
		RequestPattern requestPattern = buildRequestPatternFrom(request.getBodyAsString());
		int matchingRequestCount = requestJournal.countRequestsMatching(requestPattern);
		ResponseDefinition response = new ResponseDefinition(HTTP_OK, write(new VerificationResult(matchingRequestCount)));
		response.setHeaders(new HttpHeaders(httpHeader("Content-Type", "application/json")));
		return response;
	}

    private ResponseDefinition findRequests(Request request) {
        RequestPattern requestPattern = buildRequestPatternFrom(request.getBodyAsString());
        List<LoggedRequest> requests = requestJournal.getRequestsMatching(requestPattern);
        ResponseDefinition response = new ResponseDefinition(HTTP_OK, write(new FindRequestsResult(requests)));
        response.setHeaders(new HttpHeaders(httpHeader("Content-Type", "application/json")));
        return response;
    }

	private boolean isResetRequest(Request request) {
		return request.getMethod() == RequestMethod.POST && withoutAdminRoot(request.getUrl()).equals("/reset");
	}
	
	private boolean isResetScenariosRequest(Request request) {
		return request.getMethod() == RequestMethod.POST && withoutAdminRoot(request.getUrl()).equals("/scenarios/reset");
	}

	private boolean isNewMappingRequest(Request request) {
		return request.getMethod() == RequestMethod.POST && withoutAdminRoot(request.getUrl()).equals("/mappings/new");
	}
	
	private boolean isRequestCountRequest(Request request) {
		return request.getMethod() == RequestMethod.POST && withoutAdminRoot(request.getUrl()).equals("/requests/count");
	}

    private boolean isFindRequestsRequest(Request request) {
        return request.getMethod() == RequestMethod.POST && withoutAdminRoot(request.getUrl()).equals("/requests/find");
    }

    private boolean isSocketDelayRequest(Request request) {
        return request.getMethod() == RequestMethod.POST && withoutAdminRoot(request.getUrl()).equals("/socket-delay");
    }

	private static String withoutAdminRoot(String url) {
	    return url.replace(ADMIN_CONTEXT_ROOT, "");
	}
	
}
