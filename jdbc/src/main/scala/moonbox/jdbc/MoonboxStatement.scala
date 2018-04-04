package moonbox.jdbc

import java.sql._

import moonbox.grid.deploy.transport.model._
import moonbox.util.MoonboxJDBCUtils

class MoonboxStatement(connection: MoonboxConnection) extends Statement {

  var EXEUCTE_QUERY_TIMEOUT = 60000
  val DEFAULT_FETCH_SIZE = 200

  var dataFetchId: Long = _
  var totalRows: Long = _
  var maxFieldSize: Int = _
  var jdbcSession: JdbcSession = connection.getSession()
  var resultSet: MoonboxResultSet = _
  var maxRows: Int = 0

  /**
    * Check if the statement is closed.
    *
    * @return true if a reconnect was required
    */
  def checkClosed: Boolean = {
    if (connection == null)
      throw new Exception("Exception while execute query, because the connection is null value")
    else {
      connection.checkClosed
      if (jdbcSession != connection.getSession()) {
        jdbcSession = connection.getSession()
        true
      } else false
    }
  }

  override def executeQuery(sql: String): ResultSet = {
    checkClosed
    val client = jdbcSession.jdbcClient
    val messageId = client.getMessageId()
    val username = jdbcSession.user
    val queryMessage = JdbcQueryInbound(messageId, client.clientId, username, getFetchSize, sql)
    val resp = client.sendAndReceive(queryMessage, EXEUCTE_QUERY_TIMEOUT)
    jdbcMessage2ResultSet(queryMessage, resp)
  }

  def jdbcMessage2ResultSet(send: JdbcInboundMessage, response: Any): ResultSet = {
    response match {
      case resp: JdbcQueryOutbound =>
        if (resp.err.isDefined) {
          // Received an error message, then throw an exception
          throw new SQLException(s"sql query error: ${resp.err.get}")
          // TODO: Or retry several times (retransmit the query message) ?
        } else {
          val data = resp.data
          resultSet = new MoonboxResultSet(connection, this, data, resp.schema)
          resultSet.updateResultSet(resp)
          resultSet
        }
      case dataFetch: DataFetchOutbound =>
        if (dataFetch.err.isDefined) {
          // Received an error message, then throw an exception
          throw new SQLException(s"sql query error: ${dataFetch.err.get}")
          // TODO: Or retry several times (retransmit the query message) ?
        } else {
          val data = dataFetch.data
          val fetchState = dataFetch.dataFetchState
          dataFetchId = fetchState.messageId
          totalRows = fetchState.totalRows
          resultSet = new MoonboxResultSet(connection, this, data, dataFetch.schema)
          resultSet.updateResultSet(dataFetch)
          resultSet
        }
      case null => throw new Exception("sql query error or timeout")
      case _ => throw new Exception("Response message type error for sql query") // TODO: retry or not ?
    }
  }

  override def executeUpdate(sql: String) = 0

  override def close() = {
    resultSet = null
    jdbcSession = null
  }

  override def getMaxFieldSize = maxFieldSize

  override def setMaxFieldSize(max: Int) = {
    checkClosed
    if (max > 0)
      maxFieldSize = max
  }

  override def getMaxRows = maxRows

  override def setMaxRows(max: Int) = {
    checkClosed
    if (max > 0)
      maxRows = max
  }

  override def setEscapeProcessing(enable: Boolean) = {}

  override def getQueryTimeout = EXEUCTE_QUERY_TIMEOUT

  override def setQueryTimeout(seconds: Int) = {
    EXEUCTE_QUERY_TIMEOUT = seconds * 1000
  }

  override def cancel() = {}

  override def getWarnings = null

  override def clearWarnings() = {}

  override def setCursorName(name: String) = {}

  override def execute(sql: String) = false

  override def getResultSet = resultSet

  override def getUpdateCount = 0

  override def getMoreResults = false

  override def setFetchDirection(direction: Int) = {}

  override def getFetchDirection = 0

  override def setFetchSize(rows: Int) = {
    checkClosed
    if (rows > 0 && maxRows > 0 && rows > maxRows)
      throw new SQLException("fetchSize may not larger than maxRows")
    val props = jdbcSession.connectionProperties
    props.setProperty(MoonboxJDBCUtils.FETCH_SIZE, rows.toString)
  }

  override def getFetchSize = {
    checkClosed
    jdbcSession.connectionProperties.getProperty(MoonboxJDBCUtils.FETCH_SIZE, DEFAULT_FETCH_SIZE.toString).toInt
  }

  override def getResultSetConcurrency = 0

  override def getResultSetType = 0

  override def addBatch(sql: String) = {}

  override def clearBatch() = {}

  override def executeBatch = null

  override def getConnection = {
    checkClosed
    connection
  }

  override def getMoreResults(current: Int) = false

  override def getGeneratedKeys = null

  override def executeUpdate(sql: String, autoGeneratedKeys: Int) = 0

  override def executeUpdate(sql: String, columnIndexes: scala.Array[Int]) = 0

  override def executeUpdate(sql: String, columnNames: scala.Array[String]) = 0

  override def execute(sql: String, autoGeneratedKeys: Int) = false

  override def execute(sql: String, columnIndexes: scala.Array[Int]) = false

  override def execute(sql: String, columnNames: scala.Array[String]) = false

  override def getResultSetHoldability = 0

  override def isClosed = false

  override def setPoolable(poolable: Boolean) = {}

  override def isPoolable = false

  override def closeOnCompletion() = {}

  override def isCloseOnCompletion = false

  override def unwrap[T](iface: Class[T]) = null.asInstanceOf[T]

  override def isWrapperFor(iface: Class[_]) = false
}