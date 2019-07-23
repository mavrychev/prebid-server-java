package org.prebid.server.it;

import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToIgnoreCase;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.restassured.RestAssured.given;
import static java.util.Collections.singletonList;

@RunWith(SpringRunner.class)
public class SharethroughTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromSharethrough() throws IOException, JSONException {
        // given
        wireMockRule.stubFor(post(urlPathEqualTo("/sharethrough-exchange"))
                .withQueryParam("placement_key", equalTo("abc123"))
                .withQueryParam("bidId", equalTo("bid"))
                .withQueryParam("consent_required", equalTo("false"))
                .withQueryParam("consent_string", equalTo("consentValue"))
                .withQueryParam("instant_play_capable", equalTo("true"))
                .withQueryParam("stayInIframe", equalTo("true"))
                .withQueryParam("height", equalTo("50"))
                .withQueryParam("width", equalTo("50"))
                .withQueryParam("supplyId", equalTo("FGMrCMMc"))
                .withQueryParam("strVersion", equalTo("1.0.0"))
                .withHeader("Content-Type", equalToIgnoreCase("application/json;charset=utf-8"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Origin", equalTo("www.example.com"))
                .withRequestBody(equalTo(""))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/sharethrough/test-sharethrough-bid-response-1.json"))));

        // pre-bid cache
        wireMockRule.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/sharethrough/test-cache-sharethrough-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/sharethrough/test-cache-sharethrough-response.json"))));

        // when
        final Response response = given(spec)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "192.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for {"uids":{"sharethrough":"STR-UID"}}
                .cookie("uids", "eyJ1aWRzIjp7InNoYXJldGhyb3VnaCI6IlNUUi1VSUQifX0=")
                .body(jsonFrom("openrtb2/sharethrough/test-auction-sharethrough-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/sharethrough/test-auction-sharethrough-response.json",
                response, singletonList("sharethrough"));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }
}
