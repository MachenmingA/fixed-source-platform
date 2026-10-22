package com.fixedsource.mobile

import com.fixedsource.mobile.data.ApiClient
import com.fixedsource.mobile.data.SessionExpiredException
import com.fixedsource.mobile.data.TaskDetail
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File

class ApiClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: ApiClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = ApiClient({ server.url("/").toString().trimEnd('/') }, { "test-token" })
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `login posts credentials and parses token`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"token":"abc123","username":"inspector","name":"李现场",
                       "role":"inspector","roleLabel":"现场核查人员"}"""
                )
        )
        val result = client.login(server.url("/").toString(), "inspector", "123456")
        assertEquals("abc123", result.token)
        assertEquals("李现场", result.name)

        val request = server.takeRequest()
        assertEquals("/api/mobile/auth/login", request.path)
        assertEquals("POST", request.method)
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"username\":\"inspector\""))
        assertTrue(body.contains("\"password\":\"123456\""))
    }

    @Test
    fun `task list handles unauthorized`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"message":"登录状态已失效，请重新登录"}""")
        )
        try {
            client.currentUser()
            fail("expected SessionExpiredException")
        } catch (e: SessionExpiredException) {
            assertTrue(e.message!!.contains("登录状态"))
        }
    }

    @Test
    fun `task detail parses evidence citations and feedback`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{
                      "id":3,
                      "taskNo":"TSK-20261021-4666",
                      "title":"二氧化硫 · 浓度超限",
                      "status":"DISPATCHED",
                      "slaHours":24,
                      "dueAt":"2026-10-22 11:16",
                      "enterpriseName":"华辰热电有限公司",
                      "clue":{
                        "clueNo":"CL001",
                        "ruleCode":"R001",
                        "ruleName":"浓度超限",
                        "pointName":"1号机组废气监测点",
                        "pollutantName":"二氧化硫",
                        "triggerValue":90.0,
                        "thresholdValue":50.0,
                        "credibility":98,
                        "startTime":"2026-10-20 10:00",
                        "endTime":"2026-10-20 13:00",
                        "evidence":{"values":["10:00 实测 90.00，限值 50.00"]},
                        "aiContent":{"explanation":"需现场核实","refusal":false,
                          "suggestions":["核实治理设施运行记录"],
                          "uncertainty":"需人工复核"},
                        "aiCitations":[{"law":"中华人民共和国大气污染防治法","clause":"第十八条",
                          "title":"企业污染防治责任","effectiveDate":"2018-10-26",
                          "quote":"企业应当符合排放标准"}]
                      },
                      "feedback":null
                    }"""
                )
        )
        val task: TaskDetail = client.task("TSK-20261021-4666")
        assertEquals("华辰热电有限公司", task.enterpriseName)
        assertEquals(24, task.slaHours)
        assertEquals("1号机组废气监测点", task.clue?.pointName)
        assertEquals(90.0, task.clue?.triggerValue!!, 0.01)
        assertEquals(1, task.clue?.aiCitations?.size)
        assertEquals("第十八条", task.clue?.aiCitations?.first()?.clause)
        assertTrue(task.clue?.aiContent?.suggestions?.isNotEmpty() == true)

        val request = server.takeRequest()
        assertEquals("test-token", request.getHeader("X-Token"))
        assertEquals("/api/mobile/tasks/TSK-20261021-4666", request.path)
    }

    @Test
    fun `upload evidence sends multipart file`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"name":"evidence.jpg","url":"/uploads/e.jpg","size":12}""")
        )
        val file = File.createTempFile("evidence", ".jpg").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4))
        }
        try {
            val result = client.uploadEvidence(file)
            assertEquals("/uploads/e.jpg", result.url)

            val request = server.takeRequest()
            assertEquals("/api/files", request.path)
            assertEquals("test-token", request.getHeader("X-Token"))
            assertTrue(request.getHeader("Content-Type")!!.startsWith("multipart/form-data"))
        } finally {
            file.delete()
        }
    }

    @Test
    fun `base url normalization strips api suffix and infers scheme`() {
        assertEquals("http://10.0.2.2:8080", ApiClient.normalizeBaseUrl("10.0.2.2:8080/api"))
        assertEquals("http://192.168.1.8:8080", ApiClient.normalizeBaseUrl("http://192.168.1.8:8080/"))
        assertEquals("https://demo.example.com", ApiClient.normalizeBaseUrl("https://demo.example.com/api/"))
        assertEquals(null, ApiClient.normalizeBaseUrl(""))
        assertNotNull(ApiClient.normalizeBaseUrl("localhost:8088"))
    }

    @Test
    fun `network timeout surfaces as io exception`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        try {
            client.tasks()
            fail("expected io exception")
        } catch (e: Exception) {
            assertTrue(e is java.io.IOException)
        }
    }
}
