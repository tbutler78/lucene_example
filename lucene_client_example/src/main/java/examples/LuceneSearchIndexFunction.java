package examples;

import java.util.ArrayList;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.geode.cache.Cache;
import org.apache.geode.cache.CacheFactory;
import org.apache.geode.cache.execute.Function;
import org.apache.geode.cache.execute.FunctionContext;
import org.apache.geode.cache.lucene.LuceneIndex;
import org.apache.geode.cache.lucene.LuceneQuery;
import org.apache.geode.cache.lucene.LuceneQueryException;
import org.apache.geode.cache.lucene.LuceneResultStruct;
import org.apache.geode.cache.lucene.LuceneService;
import org.apache.geode.cache.lucene.LuceneServiceProvider;
import org.apache.geode.cache.lucene.PageableLuceneQueryResults;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.queryparser.flexible.core.QueryNodeException;
import org.apache.lucene.queryparser.flexible.standard.StandardQueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;

/**
 * The LuceneSearchIndexFunction class is a function used to collect the information on a particular
 * lucene index.
 * </p>
 * 
 * @see Cache
 * @see org.apache.geode.cache.execute.Function
 * @see FunctionAdapter
 * @see FunctionContext
 * @see InternalEntity
 * @see LuceneIndexDetails
 * @see LuceneIndexInfo
 */
@SuppressWarnings("unused")
public class LuceneSearchIndexFunction<K, V> implements Function {

  protected Cache getCache() {
    return CacheFactory.getAnyInstance();
  }

  public String getId() {
    return LuceneSearchIndexFunction.class.getSimpleName();
  }

  private LuceneQueryInfo createQueryInfoFromString(String strParm) {
    String params[] = strParm.split(",");
    //  "personIndex,Person,name:Tom99*,name,-1,false"
    int limit = Integer.parseInt((String)params[4]);
    boolean isKeyOnly = Boolean.parseBoolean((String)params[5]);
    LuceneQueryInfo queryInfo = new LuceneQueryInfo((String)params[0] /*index name */, 
        (String)params[1] /* regionPath */, 
        (String)params[2] /* queryString */,
        (String)params[3] /* default field */,
        limit, isKeyOnly);
    return queryInfo;
  }
  
  private LuceneQueryInfo createQueryInfoFromString(Object[] params) {
    //  "personIndex,Person,name:Tom99*,name,-1,false"
    int limit = (int)params[4];
    boolean isKeyOnly = (boolean)params[5];
    LuceneQueryInfo queryInfo = new LuceneQueryInfo((String)params[0] /*index name */, 
        (String)params[1] /* regionPath */, 
        (String)params[2] /* queryString */,
        (String)params[3] /* default field */,
        limit, isKeyOnly);
    return queryInfo;
  }
  
  public void execute(final FunctionContext context) {
//    Set<LuceneSearchResults> result = new HashSet<>();
    final Cache cache = getCache();
    LuceneQueryInfo queryInfo = null;
    Object args = context.getArguments();
    if (args instanceof LuceneQueryInfo) {
      queryInfo = (LuceneQueryInfo)args;
    } else if (args instanceof String) {
      String strParm = (String)args;
      queryInfo = createQueryInfoFromString(strParm);
    } else if (args instanceof Object[]) {
      queryInfo = createQueryInfoFromString((Object[])args);
      /* for curl -X POST -H 'Content-Type: application/json' -H 'Accept: application/json' -d '[{"@type": "string","@value": "personIndex"},{"@type": "string","@value": "Person"},{"@type": "string","@value": "name:Tom99*"},{"@type": "string","@value": "name"},{"@type": "int","@value": -1},{"@type": "boolean","@value": false}]' 'http://localhost:8081/geode/v1/functions/LuceneSearchIndexFunction?onRegion=Person' */
    }

    LuceneService luceneService = LuceneServiceProvider.get(getCache());
    try {
      if (luceneService.getIndex(queryInfo.getIndexName(), queryInfo.getRegionPath()) == null) {
        throw new Exception("Index " + queryInfo.getIndexName() + " not found on region "
            + queryInfo.getRegionPath());
      }
//      final LuceneQuery<K, V> query = luceneService.createLuceneQueryFactory()
//          .setLimit(queryInfo.getLimit()).create(queryInfo.getIndexName(),
//              queryInfo.getRegionPath(), queryInfo.getQueryString(), queryInfo.getDefaultField());
      final LuceneQuery<K, V> query = luceneService.createLuceneQueryFactory()
              .setLimit(queryInfo.getLimit()).create(queryInfo.getIndexName(),
                      queryInfo.getRegionPath(), index -> {
                        return new BooleanQuery.Builder()
                                .add(IntPoint.newRangeQuery("SSN", 995, 1000), BooleanClause.Occur.MUST)
                                .build();
                      });

      if (queryInfo.getKeysOnly()) {
//        query.findKeys().forEach(key -> result.add(new LuceneSearchResults(key.toString())));
        context.getResultSender().lastResult(query.findKeys());
      } else {
        PageableLuceneQueryResults<K, V> pageableLuceneQueryResults = query.findPages();
        List<LuceneResultStruct<K, V>> pageResult = new ArrayList();
        while (pageableLuceneQueryResults.hasNext()) {
          List<LuceneResultStruct<K, V>> page = pageableLuceneQueryResults.next();
          pageResult.addAll(page);
//          page.stream()
//          .forEach(searchResult -> {
//            result.add(new LuceneSearchResults<K, V>(searchResult.getKey().toString(),
//                searchResult.getValue().toString(), searchResult.getScore())); 
//          });
        }
        context.getResultSender().lastResult(pageResult);
      }
//      if (result != null) {
//        context.getResultSender().lastResult(result);
//      }
    } catch (LuceneQueryException e) {
//      result.add(new LuceneSearchResults(true, e.getRootCause().getMessage()));
      context.getResultSender().lastResult(new LuceneSearchResults(true, e.getRootCause().getMessage()));
    } catch (Exception e) {
//      result.add(new LuceneSearchResults(true, e.getMessage()));
      context.getResultSender().lastResult(new LuceneSearchResults(true, e.getMessage()));
    }
  }
}
