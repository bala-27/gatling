/**
 * Copyright 2011-2013 eBusiness Information, Groupe Excilys (www.ebusinessinformation.fr)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 		http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/**
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 		http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gatling.http.action

import com.typesafe.scalalogging.slf4j.Logging

import akka.actor.ActorRef
import io.gatling.core.action.{ Failable, Interruptable }
import io.gatling.core.session.{ Expression, Session }
import io.gatling.http.ahc.{ HttpClient, HttpTx, RequestFactory }
import io.gatling.http.cache.{ CacheHandling, URICacheKey }
import io.gatling.http.check.HttpCheck
import io.gatling.http.config.HttpProtocol
import io.gatling.http.referer.RefererHandling
import io.gatling.http.response.{ ResponseBuilder, ResponseTransformer }

object HttpRequestAction extends Logging {

	def handleHttpTransaction(tx: HttpTx) {
		if (CacheHandling.isCached(tx.protocol, tx.session, tx.request.getURI.toCacheKey)) {
			logger.info(s"Skipping cached request=${tx.requestName} uri=${tx.request.getURI}: scenario=${tx.session.scenarioName}, userId=${tx.session.userId}")
			tx.next ! tx.session

		} else {
			logger.info(s"Sending request=${tx.requestName} uri=${tx.request.getURI}: scenario=${tx.session.scenarioName}, userId=${tx.session.userId}")
			HttpClient.startHttpTransaction(tx)
		}
	}
}

/**
 * This is an action that sends HTTP requests
 *
 * @constructor constructs an HttpRequestAction
 * @param requestName the name of the request
 * @param next the next action that will be executed after the request
 * @param requestBuilder the builder for the request that will be executed
 * @param checks the checks that will be performed on the response
 * @param protocol the protocol specific configuration
 */
class HttpRequestAction(
	requestName: Expression[String],
	val next: ActorRef,
	requestFactory: RequestFactory,
	checks: List[HttpCheck],
	responseTransformer: Option[ResponseTransformer],
	maxRedirects: Option[Int],
	throttled: Boolean,
	protocol: HttpProtocol) extends Interruptable with Failable {

	val responseBuilderFactory = ResponseBuilder.newResponseBuilderFactory(checks, responseTransformer, protocol)

	def executeOrFail(session: Session) =
		for {
			resolvedRequestName <- requestName(session)
			request <- requestFactory(session, protocol)
			newSession = RefererHandling.storeReferer(request, session, protocol)
			tx = HttpTx(newSession, request, resolvedRequestName, checks, responseBuilderFactory, protocol, next, maxRedirects, throttled)

		} yield HttpRequestAction.handleHttpTransaction(tx)
}
