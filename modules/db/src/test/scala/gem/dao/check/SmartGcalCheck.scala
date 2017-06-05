package gem.dao
package check

class SmartGcalCheck extends Check {
  import SmartGcalDao.Statements._
  "SmartGcalDao.Statements" should
            "selectF2ByLamp"            in check(selectF2ByLamp(Dummy.f2SmartGcalKey)(Dummy.gcalLampType))
  it should "selectGmosNorthByLamp"     in check(selectGmosNorthByLamp(Dummy.gmosNorthSmartGcalKey)(Dummy.gcalLampType))
  it should "selectGmosSouthByLamp"     in check(selectGmosSouthByLamp(Dummy.gmosSouthSmartGcalKey)(Dummy.gcalLampType))
  it should "selectF2ByBaseline"        in check(selectF2ByBaseline(Dummy.f2SmartGcalKey)(Dummy.gcalBaselineType))
  it should "selectGmosNorthByBaseline" in check(selectGmosNorthByBaseline(Dummy.gmosNorthSmartGcalKey)(Dummy.gcalBaselineType))
  it should "selectGmosSouthByBaseline" in check(selectGmosSouthByBaseline(Dummy.gmosSouthSmartGcalKey)(Dummy.gcalBaselineType))
  it should "insertSmartF2"             in check(insertSmartF2(Dummy.gcalLampType, Dummy.gcalBaselineType, 0, Dummy.f2SmartGcalKey))
}
