import cn.xiaofei.rudp.RDatagram;
import cn.xiaofei.rudp.RDatagramChannel;
import cn.xiaofei.rudp.ReceiveListener;
import cn.xiaofei.rudp.SendTask;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class TestClient {
    public static void main(String[] args) {
        RDatagramChannel channel1 = RDatagramChannel.open(5855);
        channel1.setReceiveListener(rDatagram -> {
            String s = new String(rDatagram.data.array(), StandardCharsets.UTF_8);
            InetSocketAddress address = rDatagram.address;
            System.out.println("data: " + s + ", address: " + address);
            byte[] reply = "k".getBytes();
            channel1.send(ByteBuffer.wrap(reply), address);
        });
        String s = "和平国际社会的理念可追溯至1795年，伊曼努尔·康德在该年出版的《永久和平论》（Perpetual Peace: A Philosophical Sketch）一书中，提出代议制政府与世界联邦的构想。\n" +
                "而欧洲协调（1814年－1870年）亦于19世纪拿破仑战争后开始发展，这帮助维持欧洲安全状况，以避免发生战争。这时期亦促进了国际法律（包括日内瓦公约及海牙公约）的发展，亦为国际法中的人道主义定下了标准。\n" +
                "1889年，英国和平主义者威廉·兰德尔·克里默及法国和平主义者弗雷德里克·帕西成立国联的先驱－各国议会联盟（Inter-Parliamentary Union，简称IPU，又译‘国际国会联盟’）。当时世界约百多个国家国会，就有三分之一是各国议会联盟的会员；\n" +
                "1914年，有24个拥有国会的国家是各国议会联盟会员。其使命是鼓励各政府以和平手法去解决国际争论，年度会议则协助政府精简国际仲裁的程序。其架构内包含一个议会，这个议会就成为国联架构的前身。\n" +
                "20世纪初，德国、奥匈组成同盟国，而英国、法国、俄国和意大利组成协约国阵营。欧洲从此分为两大阵营，后来演变为第一次世界大战。这是第一次工业国家之间的战争及工业化带来的“成果”，这场“工业战争”造成难以估计的死伤及经济损失。\n" +
                "战争结束后，亦带来极大的冲击及影响全欧洲的社会、政治和经济系统。 此时，全球反战争浪潮升温，人们将一战形容为“停止所有战争的战争”（the war to end all wars）。 有些人认为战争爆发的原因是军备竞赛、同盟对立、秘密外交、君主国家的自由和新帝国主义。另一方面，有识之士希望建立一个国际组织，以国际合作的形式，共同处理纠纷。\n" +
                "早在一战进行期间，一些政府和小组早已开始发展改变国际关系的计划，避免世界大战再度发生。英国外交大臣爱德华·格雷（Edward Grey）被公认为是第一个最先提出建立国联的人。美国总统伍德罗·威尔逊和其顾问爱德华·豪斯（Edward M. House）上校对这个建议很感兴趣，认为可以避免战争，不至于重蹈一战的覆辙。关于国联的构思亦源于威尔逊的《十四点和平原则》的最后一点：“成立国际联合组织，各国互相保证彼此的政治独立、领土完整。” [3] \n" +
                "国际联盟开幕典礼\n" +
                "国际联盟开幕典礼\n" +
                "一战完结后，在1919年1月28日的巴黎和会中，通过建立国际联盟的草拟法案，并在英法两国的操纵下，派一个以威尔逊为首的起草委员会来草拟《国际联盟盟约》（Covenant of the League of Nations），准备筹组国联。\n" +
                "1919年4月28日，盟约得到44个国家签订（44个国家中有31个国家是战时支持三国协约或者加入协约国）。\n" +
                "1920年1月10日《凡尔赛条约》正式生效的这一天，在威尔逊主持下国际联盟宣告正式成立。凡是在大战中对同盟国宣战的国家和新成立的国家都是国际联盟的创始会员国。虽然威尔逊致力促成国联的成立，并得到诺贝尔和平奖，但因与英、法争夺领导权失败，美国最终未加入国联。1920年1月19日美国参议院拒绝批准《凡尔赛条约》及《国际联盟盟约》，并否决加入国联。\n" +
                "国联第一次议会会议于1920年1月16日（凡尔赛条约生效后六天）在巴黎举行。同年11月，国联总部迁至日内瓦威尔逊宫，11月15日，国联在总部内举行第一次全体大会，有41个国家代表出席。\n" +
                "1922年国联签发南森护照予无国籍难民，最终被52个国家承认。20世纪20年代，国联曾成功地解决一些小纷争。但对于30年代较大的冲突及二战，国联则显得力不从心。1946年4月18日国联正式解散，由联合国所取代\n" +
                "国际联盟是第一次世界大战结束后不久成立的一\n" +
                "万国宫-前国际联盟总部\n" +
                "万国宫-前国际联盟总部\n" +
                "个国际组织。\n" +
                "在第一次世界大战期间，美国的一些和平团体积极主张建立一个调解国际纠纷的机构。美国总统威尔逊非常赞成这个主张，并将此纳入他的“十四点原则”，力主建立国际联盟这样一个组织。\n" +
                "1919年1月18日，巴黎和会召开以后，威尔逊坚持首先讨论建立国际联盟的问题，并主张把《国联盟约》列为《对德和约》的必要组成部分。但是，在英法两国的操纵下，巴黎和会决定设立一个国联盟约起草委员会，由威尔逊担任主席，这样，威尔逊首先建立国际联盟的要求未被采纳。《国联盟约》起草委员会收到许多国家和团体提出的草案和陈述书，并就对战败国的殖民地和附属地实行委任统治问题、“门罗主义”列入《盟约》问题、反对在移民问题上的种族歧视问题等展开激烈的争论。\n" +
                "《国联盟约》经过26次修改之后，于1919年4月28日在巴黎和会上通过。《凡尔赛和约》的第一部分就是《国际联盟盟约》。\n" +
                "《盟约》中确定了国际联盟的组织机构、职能、原则和会员国的义务。\n" +
                "1920年1月10日和约正式生效的这一天，在威尔逊主持下国际联盟宣告正式成立。凡是在大战中对德奥集团宣战的国家和新成立的国家都是国际联盟的创始会员国。这样，国联共有44个会员国，后来逐渐增加到63个国家，总部设在日内瓦。中国于1920年6月29日加入国际联盟。 [1] \n" +
                "国际联盟的主要机构有大会、理事会、秘书处，并附设国际法庭、国际劳工局等，其中最主要机构是理事会。《盟约》规定，美、英、法、意、日五国为常任理事国，另外还有四个非常任理事国。\n" +
                "美国虽然是倡议国之一，但因与英、法争夺领导权失败而未参加，因此，1926年德国加入国际联盟之前只有四个常任理事国。\n" +
                "国际联盟主要受英法两国操纵。\n" +
                "根据《国联盟约》，理事会的职责是：草定裁军计划，审核承担委任统治的各国提出的年度报告，保障会员国领土完整，向大会提出解决国际争端的议案，对侵略者实行经济和军事制裁等。 [2] \n" +
                "国际联盟虽然是各国为防止武装冲突、加强普遍和平与安全而建立国际机构的第一次尝试，但在实践中并没有起到维护和平的作用。《盟约》规定将德国地由国际联盟实行委任统治，事实上等于把这些地交由英法日等国实行统治，它的作用只是帮助大国重新划分势力范围，巩固了战后世界体系。\n" +
                "第二次世界大战结束以后，随着国际矛盾的发展和激化，国际联盟不可避免地走向破产的境地。\n" +
                "1946年4月国联宣告解散，财产和档案全部移交给联合国。 [4] \n" +
                "解散\n" +
                "国际联盟总部所在地:瑞士万国宫\n" +
                "国际联盟总部所在地:瑞士万国宫\n" +
                "国际联盟自成立时起便由英、法等少数大国所控制，并成为大国手中的工具。面对20世纪30年代德、意、日法西斯同盟的形成和对外扩张，由英、法控制的国联竟以牺牲中小国家的领土和主权为代价，推行绥靖政策，使国联陷于瘫痪。第二次世界大战爆发后，国联名存实亡。随着第二次世界大战的结束和联合国的成立，国联于1946年4月18日通过决议宣布解散，其所有财产和档案移交联合国。";
        RDatagramChannel rDatagramChannel = RDatagramChannel.open(5854);
        byte[] bytes = s.getBytes();
        ByteBuffer data = ByteBuffer.wrap(bytes);
        SendTask sendTask = rDatagramChannel.send(data, new InetSocketAddress("localhost", 5855))
                .onConnected(() -> System.out.println("connected"))
                .onCompleted(() -> System.out.println("completed"))
                .onFailed(() -> System.out.println("failed"));
        sendTask.onSending(() -> System.out.println("percentage: " + sendTask.getPercentage()));
        rDatagramChannel.setReceiveListener(new ReceiveListener() {
            @Override
            public void onReceived(RDatagram rDatagram) {
                System.out.println(rDatagram.address);
                System.out.println(new String(rDatagram.data.array(), StandardCharsets.UTF_8));
                rDatagramChannel.send(ByteBuffer.wrap("aaa".getBytes()), rDatagram.address);
            }
        });
    }
}
